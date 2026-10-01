package com.drone.plant.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 飞行日志投影之一：日志页表格用的行形状（全字段）。
 */
public interface FlightLogRow {

    Long getId();

    Long getJobOrderId();

    Integer getSeqNo();

    LocalDateTime getTakeoffAt();

    LocalDateTime getLandingAt();

    Integer getDurationMin();

    BigDecimal getAreaMu();

    BigDecimal getChemicalL();

    BigDecimal getWindSpeed();

    BigDecimal getTemperature();

    Boolean getAbnormal();

    String getAbnormalDesc();
}
