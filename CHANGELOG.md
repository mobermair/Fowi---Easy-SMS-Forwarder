# Changelog

All notable changes to Fowi are listed here. Versions follow [semantic versioning](https://semver.org/).

## 1.0.0

First public release.

- Forward incoming SMS that match local rules to one or more numbers.
- Rules filter by sender and message text, with several comma-separated terms, an exclude filter, optional regular expressions and time windows on chosen weekdays.
- Four-step rule editor and a rule test that shows matches, recipients and the forwarded text without sending anything.
- Forwards start with the original sender (can be turned off).
- Choose the sending SIM per rule on dual SIM phones.
- Daily SMS limit (100 by default) to guard against unexpected costs.
- History with send and delivery status, filtering, undo and resend.
- Notifications when a forward fails, is not delivered or has no delivery report after 24 hours.
- English and German, light and dark theme in the colors of the logo.
- Open source under the GNU General Public License v3.0.
