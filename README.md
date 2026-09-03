# android-sms-forwarder

Android app that forwards incoming SMS to a webhook as JSON. Built for automating gov-document scraping flows that gate behind OTPs delivered to a phone.

Kotlin + Jetpack Compose + Room + WorkManager. Min SDK 26, target 36.

## Features

- **Rule-based forwarding.** Multiple rules, each with its own sender pattern, URL, headers, SIM filter.
- **Three match modes per rule:** `EXACT`, `WILDCARD` (shell-style `*VM-HDFCBK*`, `?`), `REGEX`.
- **Optional body regex** to gate forwarding further (e.g. match only OTP messages).
- **Manifest SMS receiver + expedited WorkManager dispatch** wake the app only when a message arrives, avoiding an always-on foreground service.
- **Optional enhanced reliability mode** enables a foreground service and requests unrestricted battery use only when the user chooses it.
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

After install:

1. Grant SMS permissions on first launch.
2. If forwarding is unreliable on a device with aggressive OEM battery management, open app info and enable **Autostart** and "allow background activity" manually.
3. Alternatively, enable **Enhanced reliability** under Defaults to keep the forwarding process running and request unrestricted battery use.

## Credits

Dual-SIM bundle-key detection empirically collected by
[bogkonstantin/android_income_sms_gateway_webhook](https://github.com/bogkonstantin/android_income_sms_gateway_webhook)
(MIT) — the list in `SimSlotDetector` is ported from that project.
The rest is a fresh Kotlin/Compose/WorkManager implementation.
