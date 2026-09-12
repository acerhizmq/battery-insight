/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.model;

import android.os.Parcel;
import android.os.Parcelable;

public class BatteryInsightStats implements Parcelable {
    public int level;
    public boolean isCharging;
    public int temp;
    public int voltage;
    public int currentNow;
    public String health = "Unknown";
    public long screenOnTime;
    public long screenOffTime;
    public long deepSleepTime;
    public float activeDrainRate;
    public float idleDrainRate;
    public int totalCapacity;
    public int currentCapacity;
    public float healthPercent;
    public int cycleCount;
    public float capacityHealth;
    public float cycleHealth;
    public int status;
    public int plugged;
    public long awakeTime;
    public int batteryDrainScreenOn;
    public int batteryDrainScreenOff;
    public int minCurrent;
    public int maxCurrent;
    public int avgCurrent;
    public float powerWatts;
    
    // Settings state
    public boolean isNotificationEnabled;
    public int monitorInterval;
    public boolean isAutoResetLevelEnabled;
    public int autoResetLevel;
    public boolean isResetOnPlugged;
    public boolean isResetOnReboot;
    public boolean isBatteryAlarmEnabled;
    public int batteryLowThreshold;
    public int batteryHighThreshold;
    public int alarmFrequency;
    public boolean isFullChargeAlarmEnabled;
    public boolean isZeroCurrentAlarmEnabled;
    public String batteryAlarmSound;
    public boolean isBatteryAlarmVibrate;

    // Charging session metrics
    public int batteryChargeScreenOn;
    public int batteryChargeScreenOff;
    public long chargeScreenOnTime;
    public long chargeScreenOffTime;
    public float chargeRate;
    public int chargeCurrentAvg;

    public BatteryInsightStats() {}

    /** Returns an independent copy suitable for crossing binder boundaries. */
    public BatteryInsightStats copy() {
        Parcel parcel = Parcel.obtain();
        try {
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    protected BatteryInsightStats(Parcel in) {
        level = in.readInt();
        isCharging = in.readByte() != 0;
        temp = in.readInt();
        voltage = in.readInt();
        currentNow = in.readInt();
        health = in.readString();
        screenOnTime = in.readLong();
        screenOffTime = in.readLong();
        deepSleepTime = in.readLong();
        activeDrainRate = in.readFloat();
        idleDrainRate = in.readFloat();
        totalCapacity = in.readInt();
        currentCapacity = in.readInt();
        healthPercent = in.readFloat();
        cycleCount = in.readInt();
        capacityHealth = in.readFloat();
        cycleHealth = in.readFloat();
        status = in.readInt();
        plugged = in.readInt();
        awakeTime = in.readLong();
        batteryDrainScreenOn = in.readInt();
        batteryDrainScreenOff = in.readInt();
        minCurrent = in.readInt();
        maxCurrent = in.readInt();
        avgCurrent = in.readInt();
        powerWatts = in.readFloat();
        isNotificationEnabled = in.readByte() != 0;
        monitorInterval = in.readInt();
        isAutoResetLevelEnabled = in.readByte() != 0;
        autoResetLevel = in.readInt();
        isResetOnPlugged = in.readByte() != 0;
        isResetOnReboot = in.readByte() != 0;
        isBatteryAlarmEnabled = in.readByte() != 0;
        batteryLowThreshold = in.readInt();
        batteryHighThreshold = in.readInt();
        alarmFrequency = in.readInt();
        isFullChargeAlarmEnabled = in.readByte() != 0;
        isZeroCurrentAlarmEnabled = in.readByte() != 0;
        batteryAlarmSound = in.readString();
        isBatteryAlarmVibrate = in.readByte() != 0;
        batteryChargeScreenOn = in.readInt();
        batteryChargeScreenOff = in.readInt();
        chargeScreenOnTime = in.readLong();
        chargeScreenOffTime = in.readLong();
        chargeRate = in.readFloat();
        chargeCurrentAvg = in.readInt();
    }

    public static final Creator<BatteryInsightStats> CREATOR = new Creator<BatteryInsightStats>() {
        @Override
        public BatteryInsightStats createFromParcel(Parcel in) {
            return new BatteryInsightStats(in);
        }

        @Override
        public BatteryInsightStats[] newArray(int size) {
            return new BatteryInsightStats[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(level);
        dest.writeByte((byte) (isCharging ? 1 : 0));
        dest.writeInt(temp);
        dest.writeInt(voltage);
        dest.writeInt(currentNow);
        dest.writeString(health);
        dest.writeLong(screenOnTime);
        dest.writeLong(screenOffTime);
        dest.writeLong(deepSleepTime);
        dest.writeFloat(activeDrainRate);
        dest.writeFloat(idleDrainRate);
        dest.writeInt(totalCapacity);
        dest.writeInt(currentCapacity);
        dest.writeFloat(healthPercent);
        dest.writeInt(cycleCount);
        dest.writeFloat(capacityHealth);
        dest.writeFloat(cycleHealth);
        dest.writeInt(status);
        dest.writeInt(plugged);
        dest.writeLong(awakeTime);
        dest.writeInt(batteryDrainScreenOn);
        dest.writeInt(batteryDrainScreenOff);
        dest.writeInt(minCurrent);
        dest.writeInt(maxCurrent);
        dest.writeInt(avgCurrent);
        dest.writeFloat(powerWatts);
        dest.writeByte((byte) (isNotificationEnabled ? 1 : 0));
        dest.writeInt(monitorInterval);
        dest.writeByte((byte) (isAutoResetLevelEnabled ? 1 : 0));
        dest.writeInt(autoResetLevel);
        dest.writeByte((byte) (isResetOnPlugged ? 1 : 0));
        dest.writeByte((byte) (isResetOnReboot ? 1 : 0));
        dest.writeByte((byte) (isBatteryAlarmEnabled ? 1 : 0));
        dest.writeInt(batteryLowThreshold);
        dest.writeInt(batteryHighThreshold);
        dest.writeInt(alarmFrequency);
        dest.writeByte((byte) (isFullChargeAlarmEnabled ? 1 : 0));
        dest.writeByte((byte) (isZeroCurrentAlarmEnabled ? 1 : 0));
        dest.writeString(batteryAlarmSound);
        dest.writeByte((byte) (isBatteryAlarmVibrate ? 1 : 0));
        dest.writeInt(batteryChargeScreenOn);
        dest.writeInt(batteryChargeScreenOff);
        dest.writeLong(chargeScreenOnTime);
        dest.writeLong(chargeScreenOffTime);
        dest.writeFloat(chargeRate);
        dest.writeInt(chargeCurrentAvg);
    }
}
