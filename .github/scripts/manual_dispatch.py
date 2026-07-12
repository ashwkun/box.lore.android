import argparse
import os
import json
import firebase_admin
from firebase_admin import credentials
from firebase_admin import messaging

def send_notification(args):
    creds_json = os.environ.get('FIREBASE_CREDENTIALS')
    if not creds_json:
        raise ValueError("FIREBASE_CREDENTIALS environment variable is not set")
    
    cred_dict = json.loads(creds_json)
    cred = credentials.Certificate(cred_dict)
    firebase_admin.initialize_app(cred)
    
    decoded_body = args.body.replace('\\n', '\n')

    data_payload = {
        'title': args.title,
        'body': decoded_body,
        'type': args.type,
        'sound': getattr(args, 'sound', 'default'),
        'action_label': getattr(args, 'action_label', 'View'),
        'show_action_in_push': getattr(args, 'show_action_in_push', 'true'),
        'show_action_in_app': getattr(args, 'show_action_in_app', 'true')
    }
    
    if args.route:
        data_payload['route'] = args.route
        
    if getattr(args, 'image', None):
        data_payload['image'] = args.image
        
    topic = getattr(args, 'target', 'all_users')
        
    android_config = None
    collapse_key = getattr(args, 'collapse_key', '')
    if collapse_key:
        android_config = messaging.AndroidConfig(collapse_key=collapse_key)

    message = messaging.Message(
        data=data_payload,
        topic=topic,
        android=android_config
    )
    
    dry_run_bool = (getattr(args, 'dry_run', 'false') == 'true')
    try:
        response = messaging.send(message, dry_run=dry_run_bool)
        if dry_run_bool:
            print(f"Dry run validation succeeded for topic '{topic}': {response}")
        else:
            print(f"Successfully sent message to topic '{topic}': {response}")
    except Exception as e:
        print(f"Failed to send message: {e}")
        raise e

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Send FCM Notification')
    parser.add_argument('--title', required=True, help='Notification Title')
    parser.add_argument('--body', required=True, help='Notification Body')
    parser.add_argument('--type', required=True, help='Where to show (push/in-app/both)')
    parser.add_argument('--route', required=False, default="", help='Optional Deep Link Route')
    parser.add_argument('--image', required=False, default="", help='Optional Big Image URL')
    parser.add_argument('--target', required=False, default="all_users", help='Target audience topic')
    parser.add_argument('--dry-run', required=False, default="false", help='Validate only')
    parser.add_argument('--collapse-key', required=False, default="", help='Optional Android collapse key')
    parser.add_argument('--sound', required=False, default="default", help='Notification sound')
    parser.add_argument('--action-label', required=False, default="View", help='Action Button Text')
    parser.add_argument('--show-action-in-push', required=False, default="true", help='Show action in push')
    parser.add_argument('--show-action-in-app', required=False, default="true", help='Show action in app')
    
    args = parser.parse_args()
    send_notification(args)
