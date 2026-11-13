# NetActivity-Kotlin
Modern Android network utility built with Kotlin and Material You. Features TCP socket ping (VPN-friendly), ICMP ping, and a multi-threaded port scanner (~12 MB APK)

# NetActivity Kotlin (Material You)

A modern Android network diagnostics tool built with Kotlin and Material Design 3. Designed for fast latency testing, port availability checks, and network troubleshooting — even behind a VPN tunnel.

## Features
- **Material You (Material 3):** Dynamic color theming (`DynamicColors`) that automatically adapts to your device wallpaper on Android 12+, with full Day/Night theme support.
- **TCP Socket Ping:** Measures TCP handshake latency on any specified port (default `443`). Works reliably over VPN tunnels where ICMP packets are dropped.
- **ICMP Ping:** Classic system `/system/bin/ping` execution with live response parsing.
- **Multi-Threaded Port Scanner:** Fast concurrent scanning of common service ports (`21`, `22`, `53`, `80`, `443`, `3306`, `8080`, `25565`).
- **Live Metrics Dashboard:** Real-time cards tracking average latency (`AVG`), `MIN / MAX` spread, packet loss, and scan duration.
- **Quick Presets & Console Tools:** One-tap host chips (`8.8.8.8`, `1.1.1.1`, `google.com`, `github.com`, `192.168.1.1`) plus copy and clear log actions.

## Project Editions
- **NetActivity Kotlin** *(this repository)* — Full-featured Material You edition (~12 MB) recommended for Android 11+.
- **[NetActivity (Java)](https://github.com/Snowsniw371/NetActivity-Java)** — Ultra-lightweight classic edition (~2 MB) optimized for Android 7.0 – 10.

## Installation
Download the signed `app-release.apk` from the **[Releases](../../releases)** page and install it on your Android device (API 24+).

## Tech Stack
- **Language:** Kotlin
- **UI:** Material Components for Android (Material 3 / Dynamic Colors)
- **Concurrency:** Java `ExecutorService` & `Handler`
- Built entirely on a smartphone using **Code on the Go**.
