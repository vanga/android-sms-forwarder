# android-sms-forwarder

Android app that forwards incoming SMS to a webhook as JSON. Built for automating gov-document scraping flows that gate behind OTPs delivered to a phone.

Kotlin + Jetpack Compose + Room + WorkManager. Min SDK 26, target 36.

## Features

- **Rule-based forwarding.** Multiple rules, each with its own sender pattern, URL, headers, SIM filter.
- **Three match modes per rule:** `EXACT`, `WILDCARD` (shell-style `*VM-HDFCBK*`, `?`), `REGEX`.
- **Optional body regex** to gate forwarding further (e.g. match only OTP messages).
- **Persistent foreground service** keeps the receiver alive under Doze / OEM battery optimizers — the main reason other forwarders drop messages.
- **Dual-SIM aware.** Per-rule SIM filter; slot detection uses the empirically collected bundle-key list from bogkonstantin/android_income_sms_gateway_webhook.
- **Exponential retry via WorkManager** with configurable max retries per rule.
- **Self-signed cert bypass** toggle for internal endpoints.
- **Rule cloning and save-and-test flows** for faster setup and iteration.
- **Filterable event log** so you can inspect activity per rule.

## Payload

`POST` with `Content-Type: application/json; charset=utf-8`:

```json
{
  "from": "VM-HDFCBK",
  "text": "...",
  "sentStamp": 1730000000000,
  "receivedStamp": 1730000000123,
  "sim": "sim1"
}
```

Custom headers (JSON object) are sent alongside the POST.

## Build

Requires JDK 17+ and Android SDK with API 36 installed. The Gradle wrapper is checked in and pinned to 9.4.1 in `gradle/wrapper/gradle-wrapper.properties`.

Then:

```bash
./gradlew assembleDebug
```

## Reliability notes (read this)

Silent SMS drops on Android are almost never a code bug — they're OEM aggressive battery management. After install:

1. Grant SMS + notifications permissions on first launch.
2. Accept the battery-optimization exemption prompt.
3. On Xiaomi / Oppo / Vivo / Realme / Samsung — open app info and enable **Autostart** and "allow background activity".
4. Keep the persistent "SMS forwarding active" notification visible — killing it will let the OS terminate the process.

## Credits

Dual-SIM bundle-key detection empirically collected by
[bogkonstantin/android_income_sms_gateway_webhook](https://github.com/bogkonstantin/android_income_sms_gateway_webhook)
(MIT) — the list in `SimSlotDetector` is ported from that project.
The rest is a fresh Kotlin/Compose/WorkManager implementation.
