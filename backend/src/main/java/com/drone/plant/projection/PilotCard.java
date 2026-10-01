package com.drone.plant.projection;

import java.math.BigDecimal;

/**
 * 飞手投影之一：飞手页卡片墙形状。
 */
public interface PilotCard {

    Long getId();

    String getPilotNo();

    String getName();

    String getPhone();

    String getLicenseLevel();

    Integer getTotalSorts();

    BigDecimal getTotalAreaMu();

    String getStatus();
}
