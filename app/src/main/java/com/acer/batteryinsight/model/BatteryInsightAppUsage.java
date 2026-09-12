/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.model;

import android.os.Parcel;
import android.os.Parcelable;

public class BatteryInsightAppUsage implements Parcelable {
    public int uid;
    public String packageName;
    public String appLabel;
    public double consumedPowerMah;
    public long foregroundTimeMs;
    public long backgroundTimeMs;

    public BatteryInsightAppUsage() {}

    protected BatteryInsightAppUsage(Parcel in) {
        uid = in.readInt();
        packageName = in.readString();
        appLabel = in.readString();
        consumedPowerMah = in.readDouble();
        foregroundTimeMs = in.readLong();
        backgroundTimeMs = in.readLong();
    }

    public static final Creator<BatteryInsightAppUsage> CREATOR = new Creator<BatteryInsightAppUsage>() {
        @Override
        public BatteryInsightAppUsage createFromParcel(Parcel in) {
            return new BatteryInsightAppUsage(in);
        }

        @Override
        public BatteryInsightAppUsage[] newArray(int size) {
            return new BatteryInsightAppUsage[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(uid);
        dest.writeString(packageName);
        dest.writeString(appLabel);
        dest.writeDouble(consumedPowerMah);
        dest.writeLong(foregroundTimeMs);
        dest.writeLong(backgroundTimeMs);
    }
}
