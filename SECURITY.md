# Security

Please report security issues privately instead of opening a public issue.

DS Usage does not operate a backend. The DeepSeek web session token is encrypted
with an AES-GCM key held by Android Keystore and is only read by the app process
that performs direct HTTPS requests to `platform.deepseek.com`.

Do not attach exported app data, WebView databases, screenshots containing a
session token, or Android Keystore material to bug reports.

