package com.drone.plant.projection;

import java.math.BigDecimal;

/**
 * 无人机投影之三：作业算药量时的载荷能力形状（最大载荷 + 电池组数 + 累计用量）。
 */
public interface DronePayload {

    Long getId();

    String getBodyNo();

    BigDecimal getMaxPayload();

    Integer getBatteryPacks();

    BigDecimal getTotalAreaMu();

    BigDecimal getTotalFlightHours();
}
