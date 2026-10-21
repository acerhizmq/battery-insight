# ProGuard rules for BatteryInsight (minify is currently disabled)
# AIDL parcelables and the service are referenced reflectively by the framework.
-keep class com.acer.batteryinsight.model.** { *; }
-keep class com.acer.batteryinsight.service.** { *; }
