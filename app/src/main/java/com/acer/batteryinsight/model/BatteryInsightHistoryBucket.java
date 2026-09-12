/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.model;

import android.os.Parcel;
import android.os.Parcelable;

public class BatteryInsightHistoryBucket implements Parcelable {
    /** UTC epoch day (millis / 86_400_000) so hour 0 on different days stay distinct. */
    public int epochDay;
    public int hour;
    public long screenOnMs;
    public int drainPercent;
    public int hourlyDrainPercent;
    public long hourlyScreenOnMs;
    public int minCurrent;
    public int maxCurrent;
    public int avgCurrent;
    public int temp;

    public BatteryInsightHistoryBucket() {}

    public BatteryInsightHistoryBucket(int hour) {
        this(0, hour);
    }

    public BatteryInsightHistoryBucket(int epochDay, int hour) {
        this.epochDay = epochDay;
        this.hour = hour;
    }

    protected BatteryInsightHistoryBucket(Parcel in) {
        epochDay = in.readInt();
        hour = in.readInt();
        screenOnMs = in.readLong();
        drainPercent = in.readInt();
        hourlyDrainPercent = in.readInt();
        hourlyScreenOnMs = in.readLong();
        minCurrent = in.readInt();
        maxCurrent = in.readInt();
        avgCurrent = in.readInt();
        temp = in.readInt();
    }

    public static final Creator<BatteryInsightHistoryBucket> CREATOR = new Creator<BatteryInsightHistoryBucket>() {
        @Override
        public BatteryInsightHistoryBucket createFromParcel(Parcel in) {
            return new BatteryInsightHistoryBucket(in);
        }

        @Override
        public BatteryInsightHistoryBucket[] newArray(int size) {
            return new BatteryInsightHistoryBucket[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(epochDay);
        dest.writeInt(hour);
        dest.writeLong(screenOnMs);
        dest.writeInt(drainPercent);
        dest.writeInt(hourlyDrainPercent);
        dest.writeLong(hourlyScreenOnMs);
        dest.writeInt(minCurrent);
        dest.writeInt(maxCurrent);
        dest.writeInt(avgCurrent);
        dest.writeInt(temp);
    }
}
