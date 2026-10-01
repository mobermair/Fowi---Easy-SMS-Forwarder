# Fowi – Easy SMS-Forwarder

An Android app that checks incoming SMS messages against local forwarding rules and sends matches to one or more phone numbers.

## Behavior

- Match on a case-insensitive sender substring, message substring, or both. When both filters are set, both must match. A filter can list several comma-separated terms, one of which must occur. Rules can also skip messages containing an exclude filter, apply only on chosen weekdays and times (windows may run past midnight), and switch all filters to case-insensitive regular expressions. Rules can have an optional name that the history shows; there is no limit on the number of rules.
- Create and edit rules in a full-screen, four-step editor (message, when, recipients, send). Existing rules can jump to any step and be saved at any time.
- Forward each matching message to the rule's recipient list as a `From: <sender>` line (with the contact name when known; can be turned off in the settings), the original text, a blank line and `This is a forwarded message`.
- On dual SIM phones, choose per rule which SIM sends the forwards (requires the optional `READ_PHONE_STATE` permission; otherwise the default SMS SIM is used). A recipient in several matching rules gets the message once, from the first rule's SIM.
- Test the rules with a sample sender and text from the **Rules** tab: it shows the matching rules, recipients, SIM, the forwarded text and the SMS count without sending anything.
- Cap the SMS sent per day in the settings (100 by default, 0 = no limit; long messages count per part, resends count too). The **Rules** tab shows today's count and links to the setting. Forwards beyond the limit are not sent and trigger a notification.
- Pause or resume all forwarding with the main switch on the **Home** tab, which also lists the setup steps (SMS access, battery optimization, notifications, an active rule) and forwarding statistics that link to the matching tab.
- Record every forwarded message in the **History** tab: content, matching rules, recipients, and per-recipient send and delivery status with timestamps. Entries can be filtered to failed forwards, deleted (with undo) and resent to one or all recipients. Delivery status depends on the carrier sending delivery reports. The history keeps the latest 200 messages.
- Show contact names next to numbers in rules, history and notifications when the optional `READ_CONTACTS` permission is granted. Names are looked up live and never stored.
- Choose light, dark or system appearance and English, German or system language in the settings (gear icon), which also contain a help section, a feedback link (email to info@om21.at) and the app version.
- Show a notification when a forward cannot be sent (for example without network, over the daily limit, or with the rule's SIM missing), is reported as not delivered, or still has no delivery report 24 hours after sending.
- Keep rules and history in local app storage. The app does not upload messages or rules.
- Request `RECEIVE_SMS` and `SEND_SMS` only when the user taps **Allow**. SMS forwarding may incur carrier charges.

## Build

Open this project in Android Studio with Android SDK 35 installed, then run the `app` configuration on a device with SMS capability. The project uses Android Gradle Plugin 9.4.1 and Kotlin 2.2.10, and targets Java 17. From the command line, build with `gradlew assembleDebug` (for example with Android Studio's bundled JDK as `JAVA_HOME`).

Incoming messages that end with `This is a forwarded message` are never forwarded again, which prevents SMS loops when a recipient points back to a device running this app.

Google Play restricts SMS permissions to eligible app use cases and may require this app to be the default SMS handler. Review current Play policies before distributing through Google Play; sideloaded builds still require the user to grant Android's SMS permissions.