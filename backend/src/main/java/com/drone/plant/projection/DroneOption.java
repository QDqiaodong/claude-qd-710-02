package com.drone.plant.projection;

import java.math.BigDecimal;

/**
 * 无人机投影之二：下拉框用的瘦身形状（机身编号 + 型号 + 状态）。
 */
public interface DroneOption {

    Long getId();

    String getBodyNo();

    String getModel();

    String getStatus();

    BigDecimal getMaxPayload();
}
