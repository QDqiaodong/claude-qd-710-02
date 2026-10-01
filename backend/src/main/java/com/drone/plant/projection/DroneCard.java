package com.drone.plant.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 无人机投影之一：无人机页卡片墙用的形状。
 */
public interface DroneCard {

    Long getId();

    String getBodyNo();

    String getModel();

    String getDroneType();

    BigDecimal getMaxPayload();

    BigDecimal getSprayWidth();

    BigDecimal getTotalFlightHours();

    BigDecimal getTotalAreaMu();

    Integer getBatteryPacks();

    String getStatus();

    LocalDate getPurchaseDate();
}
