/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.service;

import com.acer.batteryinsight.model.BatteryInsightStats;
import com.acer.batteryinsight.model.BatteryInsightFlowSample;
import com.acer.batteryinsight.model.BatteryInsightHistoryBucket;
import com.acer.batteryinsight.model.BatteryInsightAppUsage;

/** {@hide} */
interface IBatteryInsightService {
    BatteryInsightStats getBatteryState();
    BatteryInsightFlowSample[] getCurrentFlow(int minutes);
    BatteryInsightHistoryBucket[] getHistory();
    BatteryInsightFlowSample[] getSessionFlow(int sessionType);
    BatteryInsightHistoryBucket[] getSessionHistory(int sessionType);
    BatteryInsightAppUsage[] getAppUsageSinceLastCharge(int maxEntries);
    void resetStats();

    boolean isEnabled();
    void setEnabled(boolean enabled);
    
    boolean isNotificationEnabled();
    void setNotificationEnabled(boolean enabled);

    void setAutoResetLevel(int level);
    void setAutoResetLevelEnabled(boolean enabled);
    void setResetOnPlugged(boolean enabled);
    void setResetOnReboot(boolean enabled);
    
    int getMonitorInterval();
    void setMonitorInterval(int intervalMs);

    void setBatteryAlarmEnabled(boolean enabled);
    void setBatteryLowThreshold(int threshold);
    void setBatteryHighThreshold(int threshold);
    void setAlarmFrequency(int frequency);
    void setFullChargeAlarmEnabled(boolean enabled);
    void setZeroCurrentAlarmEnabled(boolean enabled);
    void setBatteryAlarmSound(String uri);
    void setBatteryAlarmVibrate(boolean enabled);
}
