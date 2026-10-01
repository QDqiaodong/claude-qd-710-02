package com.drone.plant.projection;

import java.math.BigDecimal;

/**
 * 飞行日志投影之二：统计条形状（只取出要累加的三个数值 + 异常标记）。
 */
public interface FlightLogStat {

    Long getId();

    Long getJobOrderId();

    Integer getDurationMin();

    BigDecimal getAreaMu();

    BigDecimal getChemicalL();

    Boolean getAbnormal();
}
