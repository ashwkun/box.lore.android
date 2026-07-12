# Notification Sync Fix

If you recently reinstalled BoxLore or migrated to a new Android device, you might notice that you are no longer receiving push notifications for new episodes, even though the toggle switches in the app show they are turned **ON**.

Here is why this happens and how you can fix it in under a minute.

---

## Why did this happen?

1. **Database Restore**: When you reinstall BoxLore, Android's Auto Backup system automatically restores your local database (your subscriptions, listening history, and settings).
2. **New Token**: Because the app was reinstalled, your device generated a fresh Firebase Cloud Messaging (FCM) token.
3. **Missing Subscriptions**: While your settings show notifications are enabled, the new FCM token was never registered to the podcast release topics on Firebase.

---

## How to fix it (Step-by-Step)

To force your new device token to register with Firebase, follow these quick steps:

1. Open **BoxLore**.
2. Go to your **Library** and select a podcast you want notifications for.
3. Tap the **Notification Toggle** (bell icon) to turn it **OFF**.
4. Tap the toggle again to turn it **ON**.
5. Repeat this for other subscribed podcasts if necessary.

Toggling the setting off and back on forces BoxLore to send your new token to Firebase and re-subscribe you to the correct release topics.

---

## Will this happen again?

**No.** We have implemented a permanent fix in version **v1.3.7** and later. The app now automatically checks for new device installs and reconciles your notification preferences on launch, so you will never have to manually re-toggle them again.

Thank you for your patience, and happy listening!
