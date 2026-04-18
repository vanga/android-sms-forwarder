# android-sms-forwarder

Android app that forwards incoming SMS to a webhook as JSON. Built for automating gov-document scraping flows that gate behind OTPs delivered to a phone.

Kotlin + Jetpack Compose + Room + WorkManager. Min SDK 26, target 35.

## Features

- **Rule-based forwarding.** Multiple rules, each with its own sender pattern, URL, headers, SIM filter.
- **Three match modes per rule:** `EXACT`, `WILDCARD` (shell-style `*VM-HDFCBK*`, `?`), `REGEX`.
- **Optional body regex** to gate forwarding further (e.g. match only OTP messages).
- **Persistent foreground service** keeps the receiver alive under Doze / OEM battery optimizers — the main reason other forwarders drop messages.
- **Dual-SIM aware.** Per-rule SIM filter; slot detection uses the empirically collected bundle-key list from bogkonstantin/android_income_sms_gateway_webhook.
- **Exponential retry via WorkManager** with configurable max retries per rule.
- **Self-signed cert bypass** toggle for internal endpoints.

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

Requires JDK 17+ and Android SDK with API 36 installed. Gradle wrapper is pinned to 9.4.1 in `gradle/wrapper/gradle-wrapper.properties`, but the `gradlew` script + wrapper jar aren't checked in.

One-time setup (pick one):

```bash
# Option A: install gradle, generate wrapper
brew install gradle
gradle wrapper --gradle-version 9.4.1

# Option B: open project in Android Studio (Koala Feature Drop / 2026.1+)
# — it will generate the wrapper on first sync.
```

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

SIM-slot detection and the general webhook+retry approach ported from [bogkonstantin/android_income_sms_gateway_webhook](https://github.com/bogkonstantin/android_income_sms_gateway_webhook) (MIT). Everything else is a Kotlin/Compose rewrite.
