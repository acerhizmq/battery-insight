# BatteryInsight

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10.0%2B-3DDC84.svg?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

BatteryInsight is a lightweight, real-time battery analytics and hardware telemetry tool for Android. Designed for devices running AOSP, HyperOS, OneUI, and Pixel ROMs, it directly reads current, voltage, and power from kernel `sysfs` power supply nodes without incurring unnecessary background drain or UI overhead.

Built entirely with Kotlin and Jetpack Compose.

---

## Screenshots

<p align="center">
  <img src="docs/screenshots/realtime.jpg" width="24%" alt="Real-time Telemetry" />
  <img src="docs/screenshots/history.jpg" width="24%" alt="Discharge History" />
  <img src="docs/screenshots/apps.jpg" width="24%" alt="App Power Usage" />
  <img src="docs/screenshots/settings.jpg" width="24%" alt="Settings & Monet" />
</p>

---

## Key Features

- **Direct Kernel Telemetry:** Reads real-time charging and discharging current (mA), bus voltage (mV), wattage (W), and battery temperature directly from sysfs driver nodes.
- **Persistent Status Bar Monitor:** Low-overhead foreground service with real-time stats and flicker-free updates. Dynamically formats durations (`X sa Y dk` / `X dk`).
- **Deep Sleep & Idle Analytics:** Accurate tracking of Screen On, Screen Off, and kernel Deep Sleep states to identify battery drain during standby.
- **Battery Health Tracking:** Reports battery cycle count, estimated capacity, and comparison against factory design capacity.
- **Per-App Attribution:** Detailed battery consumption breakdown powered by the Android BatteryStats subsystem.
- **Smart Alarms:** Low / charge-limit / full-charge / zero-current alarms with a configurable frequency (once, every 1/5/10 %, every 5 minutes), custom sound and vibration.
- **Status Bar Icon Styles:** Battery icon, percentage, ring, temperature, current, voltage or power as the notification icon.
- **Glitch-Resistant Level Reading:** Spurious fuel-gauge readings (e.g. a momentary 1 %) are cross-checked against independent sources before they are shown or counted.
- **Material You (Monet):** Native dynamic color palette integration with support for system-adaptive launcher icons, plus AMOLED black and Liquid Glass navigation options.
- **Flexible Access Architecture:** Operates with full hardware capabilities via Root (KernelSU, Magisk, APatch) or unrooted via standard ADB permissions.

---

## Permissions & Setup

BatteryInsight operates in two distinct modes depending on device access:

### Root Mode (Recommended)
Grant superuser access when prompted by **KernelSU**, **Magisk**, or **APatch**. This allows direct access to restricted kernel sysfs nodes and hardware power rails.

### Non-Root (ADB) Mode
If your device is not rooted, grant the necessary system metrics permissions once via ADB:

```bash
adb shell pm grant com.acer.batteryinsight android.permission.BATTERY_STATS
adb shell pm grant com.acer.batteryinsight android.permission.PACKAGE_USAGE_STATS
adb shell pm grant com.acer.batteryinsight android.permission.DUMP
adb shell cmd appops set com.acer.batteryinsight GET_USAGE_STATS allow
```

Root mode never relaxes SELinux or changes sysfs permissions; restricted nodes are read through the root shell.

---

## Building from Source

### Prerequisites
- JDK 17 or higher (set `JAVA_HOME`, or `org.gradle.java.home` in your user-level `~/.gradle/gradle.properties`)
- Android SDK Platform 37 and Build-Tools 37
- Gradle 8.7 (included via wrapper)

### Build Command
```bash
git clone https://github.com/acerhizmq/battery-insight.git
cd battery-insight
./gradlew assembleRelease
```

The compiled release package will be located at:
`BatteryInsight-v<version>-Universal.apk`

Release tasks (`assemble*Release`, `bundle*Release`, `assemble`, `build`) increment `version.properties`; debug builds do not.

### Release Signing
Create a git-ignored `keystore.properties` in the project root (or set `BI_STORE_FILE`, `BI_STORE_PASSWORD`, `BI_KEY_ALIAS`, `BI_KEY_PASSWORD`):

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, release builds are signed with the local debug key. Note that switching keys means existing installs must be uninstalled once, since Android only updates apps signed with the same key.

### Tests
```bash
./gradlew testDebugUnitTest
```

---

## Support & Contact

- Developer: [acerhizm](https://github.com/acerhizmq)
- Telegram: [@acerhizm](https://t.me/acerhizm)
- Issues & Discussions: Use GitHub Issues to submit bug reports, device-specific sysfs paths, or feature requests.

---

## License

This project is licensed under the terms of the GNU General Public License v3.0 ([GPLv3](LICENSE)).
