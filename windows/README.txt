Maildesk for Windows
====================

1. Extract this folder to a location you can write to.
2. Double-click Maildesk.exe. Keep its console window open while using the app.
3. Maildesk opens in your browser at http://127.0.0.1:8080.
4. Close the console window to stop Maildesk.

The app includes its own Java runtime. No separate Java installation is needed.
Your database, encryption key, and application log are stored under:
%LOCALAPPDATA%\Maildesk

Back up the entire Maildesk folder before moving to another computer. The
database contains school, recipient, and mail history data; the encryption key
is required to read saved SMTP passwords. Keep this folder private.

To update the app, stop it, replace the extracted app files with the new
package, and keep the existing %LOCALAPPDATA%\Maildesk folder.

Maildesk listens only on this computer (127.0.0.1). Do not expose it to a
network or the internet.
