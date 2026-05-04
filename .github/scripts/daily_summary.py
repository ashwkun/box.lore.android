"""
BoxCast Daily AI Analytics Summary
Reads Turso telemetry data, generates AI insights via GitHub Models, saves to Turso, and emails the summary.
"""

import os
import sys
import json
import requests
from datetime import datetime, timedelta, timezone

# --- Constants ---
TELEMETRY_API_URL = "https://boxcast-telemetry.boxboxcric.workers.dev/query"
GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"
IST = timezone(timedelta(hours=5, minutes=30))

def query_turso(sql, args=None):
    token = os.environ.get("TELEMETRY_API_KEY")
    if not token:
        print("ERROR: TELEMETRY_API_KEY not set")
        sys.exit(1)
        
    payload = {"query": sql}
    if args:
        payload["args"] = args
        
    resp = requests.post(
        TELEMETRY_API_URL, 
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        json=payload
    )
    if resp.status_code != 200:
        print(f"ERROR querying Turso: {resp.text}")
        return []
    
    data = resp.json()
    if not data.get("success"):
        print(f"ERROR from Turso: {data.get('error')}")
        return []
    return data.get("data", [])

def get_target_date():
    """Get yesterday's date in IST, or use DATE_OVERRIDE if provided."""
    override = os.environ.get("DATE_OVERRIDE", "").strip()
    if override:
        return override
    now_ist = datetime.now(IST)
    yesterday = now_ist - timedelta(days=1)
    return yesterday.strftime("%Y-%m-%d")

def fetch_metrics(target_date):
    """Fetch aggregated metrics for the target date from Turso."""
    # DAU
    dau_res = query_turso(f"SELECT COUNT(DISTINCT device_id) as c FROM daily_heartbeats WHERE last_seen_date = '{target_date}'")
    dau = dau_res[0]['c'] if dau_res else 0
    
    # 7-day WAU
    wau_res = query_turso(f"SELECT COUNT(DISTINCT device_id) as c FROM daily_heartbeats WHERE last_seen_date >= date('{target_date}', '-7 days')")
    wau = wau_res[0]['c'] if wau_res else 0
    
    # Total Installs
    installs_res = query_turso("SELECT COUNT(DISTINCT device_id) as c FROM daily_heartbeats")
    total_installs = installs_res[0]['c'] if installs_res else 0
    
    # Aggregates (excluding debug_)
    agg_res = query_turso(f"SELECT metric_key, metric_value FROM daily_aggregates WHERE date_partition = '{target_date}' AND metric_key NOT LIKE 'debug_%'")
    
    metrics = {}
    for row in agg_res:
        k = row['metric_key'].replace('prod_', '')
        metrics[k] = metrics.get(k, 0) + row['metric_value']
        
    # Podcast Intelligence (Content Concentration)
    pod_res = query_turso(f"SELECT podcast_id, SUM(metric_value) as plays FROM podcast_intelligence WHERE date_partition = '{target_date}' AND metric_key NOT LIKE 'debug_%' AND metric_key LIKE '%podcast_plays%' GROUP BY podcast_id ORDER BY plays DESC LIMIT 10")
    podcasts = [{"id": r['podcast_id'], "plays": r['plays']} for r in pod_res]
    
    # Process Metrics
    new_installs = metrics.get('new_install', 0)
    returning = max(0, dau - new_installs)
    total_sessions = metrics.get('total_sessions', 0) + metrics.get('session_started', 0) + metrics.get('app_open', 0)
    
    total_playback_sec = metrics.get('total_playback_sec', 0)
    total_engagement_sec = metrics.get('total_engagement_sec', 0)
    
    # Averages
    listen_per_user_min = round(total_playback_sec / dau / 60) if dau > 0 else 0
    screen_per_user_min = round(total_engagement_sec / dau / 60) if dau > 0 else 0
    sessions_per_user = round(total_sessions / dau, 1) if dau > 0 else 0
    
    # Funnel & Curated
    funnel = {k: v for k, v in metrics.items() if k.startswith('funnel_')}
    curated = {k: v for k, v in metrics.items() if k.startswith('curated_')}
    
    # 7-day aggregates
    agg_7d_res = query_turso(f"SELECT metric_key, SUM(metric_value) as v FROM daily_aggregates WHERE date_partition >= date('{target_date}', '-7 days') AND metric_key NOT LIKE 'debug_%' GROUP BY metric_key")
    metrics_7d = {}
    for row in agg_7d_res:
        metrics_7d[row['metric_key'].replace('prod_', '')] = row['v']
        
    # Lifetime aggregates
    lt_res = query_turso("SELECT metric_key, SUM(metric_value) as v FROM daily_aggregates WHERE metric_key NOT LIKE 'debug_%' GROUP BY metric_key")
    lt_metrics = {}
    for row in lt_res:
        lt_metrics[row['metric_key'].replace('prod_', '')] = row['v']
        
    # Raw un-aggregated logs (Leveraging Gemini's large context window)
    raw_logs_res = query_turso(f"SELECT event_name, event_data, timestamp FROM telemetry_logs WHERE date(timestamp) = '{target_date}' ORDER BY timestamp DESC LIMIT 5000")
    
    return {
        "date": target_date,
        "dau": dau,
        "wau": wau,
        "total_installs": total_installs,
        "new_installs": new_installs,
        "returning": returning,
        "total_sessions": total_sessions,
        "sessions_per_user": sessions_per_user,
        "total_playback_hrs": round(total_playback_sec / 3600, 1),
        "total_screen_hrs": round(total_engagement_sec / 3600, 1),
        "listen_per_user_min": listen_per_user_min,
        "screen_per_user_min": screen_per_user_min,
        "top_podcasts": podcasts,
        "funnel": funnel,
        "curated": curated,
        "metrics_7d": metrics_7d,
        "lt_metrics": lt_metrics,
        "raw_logs": raw_logs_res
    }

def build_prompt(today, prev):
    system_prompt = """You are a senior product analyst for BoxCast, an Android podcast app.
Your job is to produce a highly actionable, insightful daily summary email for the solo developer.

CRITICAL RULES:
- NEVER just restate the raw numbers. The developer can see the dashboard.
- Your job is to find PATTERNS, ANOMALIES, RATIOS, and ACTIONABLE INSIGHTS.
- Look at the Today vs 7-Day vs Lifetime ratios to identify trends (e.g. "Today had a spike in audio play compared to the 7-day average").
- Flag anything suspicious or noteworthy as a ⚠️ or ✅.
- If data is sparse, state the implications rather than complaining about lack of data.

Format your response exactly as follows (use markdown):

# 📊 DAILY INSIGHT REPORT

## 🚀 The Big Picture
2-3 sentences summarizing the real story of the day (e.g. "We saw a spike in organic installs, but funnel data shows they dropped off at onboarding").

## 📈 Key Anomalies & Trends
- Bullet point 1: Insight comparing today to 7-day/lifetime.
- Bullet point 2: Insight about engagement ratio (screen vs audio).
- Bullet point 3: Insight about funnel/discovery behavior.

## 🎧 Content Strategy
- What content is resonating based on today's top podcasts?
- Are users finding things organically or via search?

## 💡 Actionable Next Steps
1. ...
2. ...
3. ..."""

    def delta(curr, prev):
        if prev == 0: return "N/A"
        pct = round(((curr - prev) / prev) * 100)
        return f"{'↑' if pct >= 0 else '↓'} {abs(pct)}%"

    user_prompt = f"""Analyze this telemetry for BoxCast app on {today['date']}:

=== TODAY ({today['date']}) ===
DAU: {today['dau']} ({delta(today['dau'], prev['dau'])} vs yesterday)
New Installs Today: {today['new_installs']} ({delta(today['new_installs'], prev['new_installs'])} vs yesterday)
Returning Users: {today['returning']}
Total Sessions: {today['total_sessions']} ({delta(today['total_sessions'], prev['total_sessions'])} vs yesterday)
Sessions/User: {today['sessions_per_user']}x

=== ENGAGEMENT (AUDIO vs SCREEN) ===
Total Audio Playback: {today['total_playback_hrs']}h ({today['listen_per_user_min']}m avg/user)
Total Screen Time (Browsing): {today['total_screen_hrs']}h ({today['screen_per_user_min']}m avg/user)
(Note: Screen Time is app open in foreground. Audio Playback can be backgrounded).

=== CONTENT CONCENTRATION ===
Top Podcasts Played Today:
{json.dumps(today['top_podcasts'], indent=2)}

=== FUNNEL & CURATED DISCOVERY (Today) ===
Onboarding/Funnel Events: {json.dumps(today['funnel'])}
Curated Section Engagement: {json.dumps(today['curated'])}

=== RECENT TRENDS (Last 7 Days) ===
WAU (7d Active): {today['wau']}
Aggregates (7d): {json.dumps(today['metrics_7d'])}

=== LIFETIME DATA ===
Total Registered Devices: {today['total_installs']}
Lifetime Aggregates: {json.dumps(today['lt_metrics'])}

=== RAW EVENT LOGS (Up to 5000 events) ===
(Use these raw, un-aggregated events to find specific user journey drop-offs, hidden correlations, sequential patterns, or exact feature usage behaviors that are lost in the aggregates above)
{json.dumps(today.get('raw_logs', []), separators=(',', ':'))}

Please generate the summary."""
    return system_prompt, user_prompt

def call_gemini(system_prompt, user_prompt):
    key = os.environ.get("GEMINI_API_KEY")
    if not key:
        print("ERROR: No Gemini token available")
        sys.exit(1)

    target_model = os.environ.get("TARGET_MODEL", "gemini-3-flash-preview").strip()
    
    # If the workflow provides a legacy gpt model, map it to gemini-3-flash-preview
    if target_model.startswith("gpt") or target_model.startswith("DeepSeek") or target_model.startswith("Meta") or target_model.startswith("gemini-2.5-pro"):
        target_model = "gemini-3-flash-preview"
        
    models_to_try = [target_model]
    if target_model != "gemini-2.5-flash":
        models_to_try.append("gemini-2.5-flash")
    
    for model in models_to_try:
        print(f"Trying model: {model}...")
        url = GEMINI_API_URL.format(model=model, key=key)
        payload = {
            "systemInstruction": {"parts": [{"text": system_prompt}]},
            "contents": [{"role": "user", "parts": [{"text": user_prompt}]}]
        }
        
        try:
            resp = requests.post(url, headers={"Content-Type": "application/json"}, json=payload, timeout=60)
            if resp.status_code == 200:
                data = resp.json()
                if "candidates" in data and len(data["candidates"]) > 0:
                    summary = data["candidates"][0]["content"]["parts"][0]["text"]
                    print(f"✅ Success with {model}")
                    return summary, model
            
            print(f"⚠️ {model} returned {resp.status_code}: {resp.text[:200]}")
        except Exception as e:
            print(f"⚠️ {model} failed: {e}")

    print("ERROR: All Gemini models failed")
    sys.exit(1)

def save_summary_turso(date_str, summary, model_id):
    sql = """INSERT INTO ai_summaries (date_partition, summary, model, generated_at) 
             VALUES (?, ?, ?, CURRENT_TIMESTAMP)
             ON CONFLICT (date_partition) DO UPDATE SET 
             summary = excluded.summary, model = excluded.model, generated_at = CURRENT_TIMESTAMP"""
    
    token = os.environ.get("TELEMETRY_API_KEY")
    resp = requests.post(
        TELEMETRY_API_URL, 
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        json={"query": sql, "args": [date_str, summary, model_id]}
    )
    if resp.status_code == 200:
        print(f"✅ Summary saved to Turso for {date_str}")
    else:
        print(f"⚠️ Failed to save to Turso: {resp.text}")

def send_email_via_proxy(date_str, summary, model_id):
    proxy_url = os.environ.get("PROXY_URL", "https://api.aswin.cx")
    app_key = os.environ.get("APP_SECRET_KEY", "").strip()
    if not app_key:
        print("⚠️ APP_SECRET_KEY not set, skipping email")
        return

    subject = f"[BoxCast] Daily Summary — {date_str} (via {model_id})"
    body = f"BoxCast Daily Analytics Summary\n{'='*40}\nDate: {date_str}\nModel: {model_id}\nGenerated: {datetime.now(IST).strftime('%Y-%m-%d %H:%M IST')}\n{'='*40}\n\n{summary}"

    try:
        resp = requests.post(
            f"{proxy_url}/send-summary-email",
            headers={"X-App-Key": app_key, "Content-Type": "application/json"},
            json={"subject": subject, "body": body, "date": date_str},
            timeout=15,
        )
        if resp.status_code == 200:
            print("✅ Email sent successfully")
    except Exception as e:
        print(f"⚠️ Email send error: {e}")

def main():
    print("🚀 BoxCast Daily AI Summary (Turso)")
    
    target_date = get_target_date()
    prev_date = (datetime.strptime(target_date, "%Y-%m-%d") - timedelta(days=1)).strftime("%Y-%m-%d")
    print(f"📅 Target date: {target_date} (prev: {prev_date})")

    print("📡 Fetching Turso data...")
    today_data = fetch_metrics(target_date)
    prev_data = fetch_metrics(prev_date)

    print("🤖 Generating AI summary...")
    sys_prompt, user_prompt = build_prompt(today_data, prev_data)
    print("Calling Gemini...")
    summary, model_id = call_gemini(sys_prompt, user_prompt)

    print(f"\n{'='*40}\n{summary}\n{'='*40}\n")

    save_summary_turso(target_date, summary, model_id)
    send_email_via_proxy(target_date, summary, model_id)
    print("🎉 Done!")

if __name__ == "__main__":
    main()
