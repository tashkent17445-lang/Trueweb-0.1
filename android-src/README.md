# TrueWeb Android v0.8.7

Market-ready Android release for TrueWeb VPN.

## Connection policy

The UI exposes one connect control. Routing is automatic:

1. primary 3x-ui inbound ID **1**;
2. backup inbound ID **18** if primary is unreachable;
3. WL emergency path only if both ordinary entries are unreachable and WL itself is reachable.

WL is not user-selectable. While WL is active, ordinary paths are checked roughly every 170–210 seconds and TrueWeb returns to ordinary routing only after three consecutive successful probes separated by four seconds. If the active WL path dies, the full `primary -> backup -> WL` chain is re-evaluated immediately. Physical Wi-Fi/LTE loss does not mark servers as failed; the desired VPN state is retained until a physical network returns.

## Authentication

- email + one-time 6-digit code;
- Telegram;
- optional login + password configured by the authenticated user.

Passwords are expected to be stored by the backend only as a salted memory-hard hash. No server credentials, Telegram bot token, SMTP password or 3x-ui administrative credential is embedded into the APK.

## Account and legal UI

The application includes:

- first-run explicit privacy consent;
- Privacy Policy;
- User Agreement;
- Acceptable Use Rules;
- Account and Data Deletion policy;
- account deletion control;
- login/password setup;
- official Telegram group link;
- operator identification: **Клячко Павел Владимирович**.

The account deletion backend keeps only the minimum pseudonymous anti-abuse marker needed to prevent repeated one-time trial claims, plus records that may need to be retained for payment/accounting/legal purposes.

## Diagnostics

The release UI does not expose or persist application debug logs. Unexpected authenticated runtime errors are sent through the TrueWeb API to the backend, where the administrator can receive a Telegram notification. Reports are deduplicated and must not contain bearer tokens, subscription URLs, complete VLESS URIs, authentication codes or user traffic content.

Android/native libraries may still emit operating-system `logcat` messages; TrueWeb code intentionally avoids writing sensitive connection credentials there.

## Network traffic privacy

TrueWeb does not intentionally inspect, record, sell or profile the contents of user Internet traffic. The VPN server necessarily processes packets and technical routing metadata. TrueWeb does not install interception certificates and does not perform TLS/HTTPS MITM decryption. Content protected by HTTPS/TLS or end-to-end encryption is not available to the VPN service in plaintext.

## Build

- package: `ru.trueweb.vpn`
- minSdk: 26
- targetSdk: 37
- ABI: arm64-v8a
- version: 0.8.7 / versionCode 16
- VPN engine: Xray Android core supplied by the CI workflow


### Самообновление Android

Manifest: `https://trueweb24.ru:8443/android/latest.json`

Пример:
```json
{
  "version": "0.8.7",
  "version_code": 16,
  "url": "https://trueweb24.ru/download/TrueWeb.apk",
  "sha256": "<SHA256 APK>",
  "notes": "Новая версия TrueWeb"
}
```

APK обновления должен быть подписан тем же ключом, что и установленная версия.
