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
    
    # We send a Data payload so the app's FirebaseMessagingService has full control
    # over whether it displays as a system push notification, in-app banner, or both.
    data_payload = {
        'title': args.title,
        'body': args.body,
        'type': args.type
    }
    
    if args.route:
        data_payload['route'] = args.route
        
    message = messaging.Message(
        data=data_payload,
        topic='all_users'
    )
    
    try:
        response = messaging.send(message)
        print(f"Successfully sent message to topic 'all_users': {response}")
    except Exception as e:
        print(f"Failed to send message: {e}")
        raise e

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Send FCM Notification')
    parser.add_argument('--title', required=True, help='Notification Title')
    parser.add_argument('--body', required=True, help='Notification Body')
    parser.add_argument('--type', required=True, help='Where to show (push/in-app/both)')
    parser.add_argument('--route', required=False, default="", help='Optional Deep Link Route')
    
    args = parser.parse_args()
    send_notification(args)
