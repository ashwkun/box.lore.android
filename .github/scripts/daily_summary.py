"""
BoxCast Daily AI Analytics Summary
Reads Firebase RTDB data, generates AI insights via GitHub Models, emails the summary.
"""

import os
import sys
import json
import requests
from datetime import datetime, timedelta, timezone

import firebase_admin
from firebase_admin import credentials, db as firebase_db

# --- Constants ---
RTDB_URL = "https://boxcasts-default-rtdb.asia-southeast1.firebasedatabase.app"
GH_MODELS_ENDPOINT = "https://models.inference.ai.azure.com/chat/completions"
IST = timezone(timedelta(hours=5, minutes=30))

# Model fallback chain: best → most reliable
MODELS = [
    {"id": "gpt-4o", "name": "GPT-4o", "tier": "high"},
    {"id": "gpt-4o-mini", "name": "GPT-4o Mini", "tier": "low"},
    {"id": "o4-mini", "name": "O4 Mini", "tier": "reasoning"},
    {"id": "DeepSeek-R1", "name": "DeepSeek R1", "tier": "reasoning"},
]


def init_firebase():
    creds_json = os.environ.get("FIREBASE_CREDENTIALS")
    if not creds_json:
        print("ERROR: FIREBASE_CREDENTIALS not set")
        sys.exit(1)
    cred = credentials.Certificate(json.loads(creds_json))
    firebase_admin.initialize_app(cred, {"databaseURL": RTDB_URL})


def get_target_date():
    """Get yesterday's date in IST, or use DATE_OVERRIDE if provided."""
    override = os.environ.get("DATE_OVERRIDE", "").strip()
    if override:
        return override
    now_ist = datetime.now(IST)
    yesterday = now_ist - timedelta(days=1)
    return yesterday.strftime("%Y-%m-%d")


def fetch_day_data(date_str, prod_only=True):
    """Fetch all data for a given date from RTDB."""
    ref = firebase_db.reference(f"daily/{date_str}")
    data = ref.get() or {}

    devices = data.get("devices", {})
    new_users = data.get("new_users", {})
    total_sessions = data.get("total_sessions", 0)
    total_engagement_ms = data.get("total_engagement_ms", 0)
    total_playback_ms = data.get("total_playback_ms", 0)
    feature_events = data.get("feature_events", {})

    if prod_only:
        devices = {k: v for k, v in devices.items()
                   if not (v.get("v", "") or "").endswith("-debug")}
        prod_device_ids = set(devices.keys())
        new_users = {k: v for k, v in new_users.items() if k in prod_device_ids or k not in (data.get("devices", {}))}
        # Filter feature events
        for feat_id in list(feature_events.keys()):
            feature_events[feat_id] = {
                k: v for k, v in feature_events[feat_id].items()
                if not (v.get("v", "") or "").endswith("-debug")
            }
            if not feature_events[feat_id]:
                del feature_events[feat_id]

    dau = len(devices)

    # If filtering prod and no prod devices, zero out aggregates
    if prod_only and dau == 0:
        total_sessions = 0
        total_engagement_ms = 0
        total_playback_ms = 0

    # Version distribution
    versions = {}
    for d in devices.values():
        v = d.get("v", "unknown")
        versions[v] = versions.get(v, 0) + 1

    new_count = len(new_users)
    returning = max(0, dau - new_count)
    retention_pct = round((returning / dau) * 100) if dau > 0 else 0
    sessions_per_user = round(total_sessions / dau, 1) if dau > 0 else 0
    avg_engagement_min = round(total_engagement_ms / dau / 60000) if dau > 0 else 0
    avg_playback_min = round(total_playback_ms / dau / 60000) if dau > 0 else 0

    return {
        "date": date_str,
        "dau": dau,
        "new_installs": new_count,
        "returning_users": returning,
        "retention_pct": retention_pct,
        "total_sessions": total_sessions,
        "sessions_per_user": sessions_per_user,
        "total_engagement_ms": total_engagement_ms,
        "avg_engagement_min": avg_engagement_min,
        "total_playback_ms": total_playback_ms,
        "avg_playback_min": avg_playback_min,
        "version_distribution": versions,
        "feature_events": {
            feat_id: len(events)
            for feat_id, events in feature_events.items()
        },
    }


def fetch_total_installs(prod_only=True):
    """Get all-time install count from devices node."""
    ref = firebase_db.reference("devices")
    devices = ref.get() or {}
    if prod_only:
        devices = {k: v for k, v in devices.items()
                   if not (v.get("v", "") or "").endswith("-debug")}
    return len(devices)


def fetch_7day_trend(target_date_str):
    """Fetch 7 days of data ending on target_date for trend analysis."""
    target = datetime.strptime(target_date_str, "%Y-%m-%d")
    trend = []
    for i in range(6, -1, -1):
        d = target - timedelta(days=i)
        day_data = fetch_day_data(d.strftime("%Y-%m-%d"))
        trend.append(day_data)
    return trend


def compute_wau(trend_data):
    """Compute Weekly Active Users from 7-day trend (approximate from DAU sum)."""
    # We don't have per-device data for all 7 days in this context,
    # so we use max DAU as a conservative WAU estimate
    return max((d["dau"] for d in trend_data), default=0)


def build_prompt(today_data, prev_data, trend_data, total_installs, wau):
    """Construct the full system + user prompt for the AI model."""
    system_prompt = """You are an expert mobile app analytics advisor for BoxCast, an Android podcast listening app.
You analyze privacy-respecting, first-party analytics data that tracks anonymous usage metrics via Firebase Realtime Database.

Your job is to produce a concise, actionable daily summary email for the solo indie developer.

Format your response EXACTLY as follows:

📊 EXECUTIVE SUMMARY
2-3 sentences summarizing the day's health at a glance.

📈 KEY METRICS
- DAU: X (↑/↓ Y% vs yesterday)
- New Installs: X
- Retention: X% (Y returning users)
- Sessions/User: Xx
- Avg Active Time: Xm per user
- Avg Playback: Xm per user
- Total Engagement: Xh | Total Playback: Xh

🔍 INSIGHTS
Bullet points analyzing patterns, anomalies, engagement quality, version adoption, and user behavior.

💡 RECOMMENDATIONS
Actionable next steps based on the data.

⚠️ ALERTS (only if something is concerning — omit this section if everything looks healthy)
Flag any concerning metrics.

Keep the tone professional but friendly. Use emoji sparingly. Be specific with numbers. If data is sparse (low DAU), acknowledge it but still provide value."""

    # Build the data payload
    trend_summary = []
    for d in trend_data:
        trend_summary.append(
            f"  {d['date']}: DAU={d['dau']}, New={d['new_installs']}, "
            f"Sessions={d['total_sessions']}, EngAvg={d['avg_engagement_min']}m, "
            f"PlayAvg={d['avg_playback_min']}m"
        )

    feature_str = "None" if not today_data["feature_events"] else ", ".join(
        f"{fid}: {count} interactions" for fid, count in today_data["feature_events"].items()
    )

    version_str = "None" if not today_data["version_distribution"] else ", ".join(
        f"{v}: {c} users" for v, c in sorted(today_data["version_distribution"].items(), key=lambda x: -x[1])
    )

    # Compute deltas
    def delta(curr, prev):
        if prev == 0:
            return "N/A (no prev data)"
        pct = round(((curr - prev) / prev) * 100)
        arrow = "↑" if pct >= 0 else "↓"
        return f"{arrow} {abs(pct)}%"

    user_prompt = f"""Analyze this data for BoxCast app on {today_data['date']}:

=== TODAY ({today_data['date']}) ===
DAU: {today_data['dau']} ({delta(today_data['dau'], prev_data['dau'])} vs yesterday)
New Installs: {today_data['new_installs']}
Returning Users: {today_data['returning_users']}
Retention: {today_data['retention_pct']}%
Total Sessions: {today_data['total_sessions']} ({delta(today_data['total_sessions'], prev_data['total_sessions'])} vs yesterday)
Sessions/User: {today_data['sessions_per_user']}x
Total Engagement: {round(today_data['total_engagement_ms'] / 3600000, 1)}h ({today_data['avg_engagement_min']}m avg/user) ({delta(today_data['avg_engagement_min'], prev_data['avg_engagement_min'])} vs yesterday)
Total Playback: {round(today_data['total_playback_ms'] / 3600000, 1)}h ({today_data['avg_playback_min']}m avg/user) ({delta(today_data['avg_playback_min'], prev_data['avg_playback_min'])} vs yesterday)
Version Distribution: {version_str}
Feature Interactions: {feature_str}

=== YESTERDAY ({prev_data['date']}) ===
DAU: {prev_data['dau']}, New: {prev_data['new_installs']}, Sessions: {prev_data['total_sessions']}
Engagement: {prev_data['avg_engagement_min']}m avg, Playback: {prev_data['avg_playback_min']}m avg

=== 7-DAY TREND ===
{chr(10).join(trend_summary)}

=== TOTALS ===
Weekly Active Users (WAU): {wau}
All-Time Installs (prod): {total_installs}

Please generate the daily summary."""

    return system_prompt, user_prompt


def call_github_models(system_prompt, user_prompt):
    """Call GitHub Models API with cascading fallbacks."""
    token = os.environ.get("GH_MODELS_TOKEN") or os.environ.get("GITHUB_TOKEN_FALLBACK") or os.environ.get("GITHUB_TOKEN")
    if not token:
        print("ERROR: No GitHub token available for Models API")
        sys.exit(1)

    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }

    for model in MODELS:
        model_id = model["id"]
        print(f"Trying model: {model_id} ({model['name']})...")
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
                result = resp.json()
                summary = result["choices"][0]["message"]["content"]
                print(f"✅ Success with {model_id}")
                return summary, model_id
            else:
                print(f"⚠️ {model_id} returned {resp.status_code}: {resp.text[:200]}")
        except Exception as e:
            print(f"⚠️ {model_id} failed: {e}")

    print("ERROR: All models failed")
    sys.exit(1)


def write_summary_to_rtdb(date_str, summary, model_id):
    """Store the generated summary in RTDB for admin panel to display."""
    ref = firebase_db.reference(f"daily/{date_str}/ai_summary")
    ref.set({
        "summary": summary,
        "model": model_id,
        "generated_at": datetime.now(IST).isoformat(),
        "type": "automated",
    })
    print(f"✅ Summary written to RTDB at daily/{date_str}/ai_summary")


def send_email_via_proxy(date_str, summary, model_id):
    """Send the summary email via the Cloudflare Worker proxy."""
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
            headers={
                "X-App-Key": app_key,
                "Content-Type": "application/json",
            },
            json={"subject": subject, "body": body, "date": date_str},
            timeout=15,
        )
        if resp.status_code == 200:
            print("✅ Email sent successfully")
        else:
            print(f"⚠️ Email failed: {resp.status_code} {resp.text[:200]}")
    except Exception as e:
        print(f"⚠️ Email send error: {e}")


def main():
    print("🚀 BoxCast Daily AI Summary")
    print("=" * 40)

    # 1. Init Firebase
    init_firebase()

    # 2. Determine target date (yesterday in IST)
    target_date = get_target_date()
    prev_date = (datetime.strptime(target_date, "%Y-%m-%d") - timedelta(days=1)).strftime("%Y-%m-%d")
    print(f"📅 Target date: {target_date} (prev: {prev_date})")

    # 3. Fetch all data
    print("📡 Fetching RTDB data...")
    today_data = fetch_day_data(target_date)
    prev_data = fetch_day_data(prev_date)
    trend_data = fetch_7day_trend(target_date)
    total_installs = fetch_total_installs()
    wau = compute_wau(trend_data)

    print(f"   DAU: {today_data['dau']}, New: {today_data['new_installs']}, "
          f"Sessions: {today_data['total_sessions']}, Total Installs: {total_installs}")

    # 4. Build prompt and call AI
    print("🤖 Generating AI summary...")
    system_prompt, user_prompt = build_prompt(today_data, prev_data, trend_data, total_installs, wau)
    summary, model_id = call_github_models(system_prompt, user_prompt)

    print(f"\n{'='*40}")
    print(summary)
    print(f"{'='*40}\n")

    # 5. Write to RTDB
    write_summary_to_rtdb(target_date, summary, model_id)

    # 6. Send email
    send_email_via_proxy(target_date, summary, model_id)

    print("🎉 Done!")


if __name__ == "__main__":
    main()
