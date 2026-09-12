/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.model;

import android.os.Parcel;
import android.os.Parcelable;

public class BatteryInsightFlowSample implements Parcelable {
    public long timestamp;
    public int current;
    public boolean isCharging;
    public int level;
    public int voltage;
    public int temp;

    public BatteryInsightFlowSample() {}

    public BatteryInsightFlowSample(long timestamp, int current, boolean isCharging) {
        this(timestamp, current, isCharging, 0, 0, 0);
    }

    public BatteryInsightFlowSample(long timestamp, int current, boolean isCharging, int level, int voltage, int temp) {
        this.timestamp = timestamp;
        this.current = current;
        this.isCharging = isCharging;
        this.level = level;
        this.voltage = voltage;
        this.temp = temp;
    }

    protected BatteryInsightFlowSample(Parcel in) {
        timestamp = in.readLong();
        current = in.readInt();
        isCharging = in.readByte() != 0;
        level = in.readInt();
        voltage = in.readInt();
        temp = in.readInt();
    }

    public static final Creator<BatteryInsightFlowSample> CREATOR = new Creator<BatteryInsightFlowSample>() {
        @Override
        public BatteryInsightFlowSample createFromParcel(Parcel in) {
            return new BatteryInsightFlowSample(in);
        }

        @Override
        public BatteryInsightFlowSample[] newArray(int size) {
            return new BatteryInsightFlowSample[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(timestamp);
        dest.writeInt(current);
        dest.writeByte((byte) (isCharging ? 1 : 0));
        dest.writeInt(level);
        dest.writeInt(voltage);
        dest.writeInt(temp);
    }
}
