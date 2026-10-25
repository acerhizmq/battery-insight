## BatteryInsight v1.3.37 Release

> [!IMPORTANT]
> **One-time reinstall required.** Starting with this version, BatteryInsight is signed with a dedicated, permanent release key instead of a development key. Android only allows updates signed with the same key, so **v1.3.36 and earlier cannot be updated in place** — the in-app updater and the system installer will report a package conflict.
> 1. Uninstall the current BatteryInsight version (statistics and settings will be reset).
> 2. Install `BatteryInsight-v1.3.37-Universal.apk`.
> 3. **Root users:** reboot once after installing, to restore the sysfs permissions changed by previous versions.
>
> All future releases will be signed with the same key and update normally.

### Highlights
This release eliminates the intermittent 1% battery level glitch, implements the previously inactive alarm frequency setting, introduces customizable status bar icon styles and appearance options, and hardens the monitoring service for Android 15–17. Root mode no longer modifies sysfs permissions or SELinux policy.

---

### What's New
- **Battery Level Glitch Protection:**
  - New level validation engine: physically implausible jumps (e.g. a transient 1% reading) are cross-checked against BatteryManager and the sysfs capacity node, or require three consecutive confirmations before being shown or counted.
  - Initial level snapshot on app launch is validated against independent sources.
  - Removed the recursive `chmod` on `/sys/class/power_supply`, which altered node permissions system-wide and could interfere with the system's own battery readings.

- **Smart Alarm Frequency:**
  - The alarm frequency setting is now fully functional: *Once*, *Every 1% / 5% / 10%* or *Every 5 minutes*.
  - Low-battery alarm no longer repeats every minute; full-charge alarm only fires while the charger is connected.
  - Time-based zero-current (0 mA) detection with a 20-second sustain window.
  - Alarm sound respects silent and vibrate ringer modes; overlapping sounds are stopped.

- **Status Bar Icon Styles:**
  - Choose the notification icon: battery, percentage, ring, temperature, percentage & temperature, current (mA), voltage or power (W).

- **Appearance Options:**
  - AMOLED pure black theme, dynamic color (Monet) toggle and Liquid Glass navigation toggle — applied instantly without restarting.

- **Telemetry Accuracy:**
  - Per-device µA/mA detection: low deep-sleep currents (e.g. 5 mA) are no longer displayed as 5000 mA.
  - Net discharge while plugged in (charge limit, heavy load) is shown with the correct sign.
  - Min / max / average current is no longer skewed by UI polling.
  - Hourly history now uses local time (fixes misplaced 00:00–03:00 entries) and keeps hourly values across service restarts.
  - Battery health no longer confuses remaining charge with full capacity on devices without a `charge_full` node.
  - Battery health status is localized; unknown states are no longer reported as *Good*.

- **App Usage:**
  - Per-app consumption now refreshes periodically instead of once per session.
  - Fixed per-app analysis in ADB (non-root) mode.
  - Per-app percentages are calculated against total measured consumption.

- **Service & Security Hardening:**
  - Removed SELinux policy injection that granted all untrusted apps read access to sysfs.
  - Android 14+ uses the `specialUse` foreground service type only, avoiding the Android 15 `dataSync` 6-hour limit and boot-start restrictions.
  - Monitoring state is processed on a dedicated thread; the service shuts down cleanly without leaking background loops.
  - Keep-alive watchdog moved to a non-exported receiver.
  - Tapping notifications now opens the app correctly while the Monet launcher icon is enabled.

- **In-App Updater:**
  - Downloaded updates are verified for size, package name and signing certificate before installation.
  - Interrupted downloads are cleaned up automatically.

- **Fixes:**
  - Master toggle no longer disables the persistent notification setting.
  - Fixed literal `%%` in alarm frequency labels and the full-charge notification title.
  - History entries without drain no longer display the cumulative session total.

---

### Verification & Checksums
- **Package:** `BatteryInsight-v1.3.37-Universal.apk`
- **SHA-256:** `1bb017deb87fcd31e0a4a98b16df5b2ed57d9d4ac2c59ed25302f81e4d58f6e7`
- **Signing Certificate SHA-256:** `bd8a5284b36a58fdb2c32c9691c06f09648949f581dc326aecc658d82e3be256`
- **Min SDK:** Android 10.0 (API 29)
- **Target SDK:** Android 17 (API 37)

---

### Community & Feedback
- Telegram: [@acerhizm](https://t.me/acerhizm)
- Report issues or submit device-specific sysfs nodes on the GitHub Issues page.
