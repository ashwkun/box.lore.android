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
GH_MODELS_ENDPOINT = "https://models.inference.ai.azure.com/chat/completions"
IST = timezone(timedelta(hours=5, minutes=30))

# Model fallback chain
MODELS = [
    {"id": "gpt-4o", "name": "GPT-4o"},
    {"id": "DeepSeek-R1", "name": "DeepSeek R1"},
    {"id": "Meta-Llama-3.1-405B-Instruct", "name": "Llama 3.1 405B"},
    {"id": "gpt-4o-mini", "name": "GPT-4o Mini"},
]

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
        "curated": curated
    }

def build_prompt(today, prev):
    system_prompt = """You are an expert mobile app analytics advisor for BoxCast, an Android podcast app.
Your job is to produce a concise, actionable daily summary email for the solo developer.

Format your response EXACTLY as follows (use markdown):

📊 EXECUTIVE SUMMARY
2-3 sentences summarizing the day's health at a glance.

📈 KEY METRICS
- DAU: X (↑/↓ Y% vs yesterday)
- WAU (7d): X
- New Installs: X
- Sessions/User: X
- Audio Playback: Xh total (Xm avg/user)
- Screen Time: Xh total (Xm avg/user)

🎧 CONTENT & ENGAGEMENT
- Which podcasts dominated today? (Content concentration)
- Are users spending more time listening (audio) or browsing (screen)?
- Any interesting signals from the curated section or onboarding funnel?

💡 RECOMMENDATIONS
Actionable next steps based on the data.

⚠️ ALERTS
Flag any concerning metrics (e.g., massive drop in listen time, skewed power user data). Omit if everything is healthy.

Be specific with numbers. Do not refer to old insights; focus heavily on the rich new metrics provided (audio vs screen time, podcast concentration, funnel data)."""

    def delta(curr, prev):
        if prev == 0: return "N/A"
        pct = round(((curr - prev) / prev) * 100)
        return f"{'↑' if pct >= 0 else '↓'} {abs(pct)}%"

    user_prompt = f"""Analyze this data for BoxCast app on {today['date']}:

=== TODAY ({today['date']}) ===
DAU: {today['dau']} ({delta(today['dau'], prev['dau'])} vs yesterday)
WAU (7d Active): {today['wau']}
Total Lifetime Installs: {today['total_installs']}
New Installs Today: {today['new_installs']}
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

=== FUNNEL & CURATED DISCOVERY ===
Onboarding/Funnel Events: {json.dumps(today['funnel'])}
Curated Section Engagement: {json.dumps(today['curated'])}

=== YESTERDAY ({prev['date']}) ===
DAU: {prev['dau']}, Sessions: {prev['total_sessions']}
Playback: {prev['listen_per_user_min']}m avg/user, Screen: {prev['screen_per_user_min']}m avg/user

Please generate the summary."""
    return system_prompt, user_prompt

def call_github_models(system_prompt, user_prompt):
    token = os.environ.get("GH_MODELS_TOKEN") or os.environ.get("GITHUB_TOKEN_FALLBACK") or os.environ.get("GITHUB_TOKEN")
    if not token:
        print("ERROR: No GitHub token available")
        sys.exit(1)

    target_model = os.environ.get("TARGET_MODEL", "gpt-4o").strip()
    
    # Put target_model first, then fallback to others
    models_to_try = [m for m in MODELS if m["id"] == target_model]
    models_to_try += [m for m in MODELS if m["id"] != target_model]

    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    for model in models_to_try:
        model_id = model["id"]
        print(f"Trying model: {model_id}...")
        try:
            payload = {
                "model": model_id,
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                "temperature": 0.7,
                "max_tokens": 2000,
            }
            resp = requests.post(GH_MODELS_ENDPOINT, headers=headers, json=payload, timeout=60)
            if resp.status_code == 200:
                summary = resp.json()["choices"][0]["message"]["content"]
                print(f"✅ Success with {model_id}")
                return summary, model_id
            else:
                print(f"⚠️ {model_id} returned {resp.status_code}: {resp.text[:200]}")
        except Exception as e:
            print(f"⚠️ {model_id} failed: {e}")

    print("ERROR: All models failed")
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
    summary, model_id = call_github_models(sys_prompt, user_prompt)

    print(f"\n{'='*40}\n{summary}\n{'='*40}\n")

    save_summary_turso(target_date, summary, model_id)
    send_email_via_proxy(target_date, summary, model_id)
    print("🎉 Done!")

if __name__ == "__main__":
    main()
