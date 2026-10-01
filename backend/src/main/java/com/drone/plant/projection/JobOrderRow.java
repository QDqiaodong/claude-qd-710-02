package com.drone.plant.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 作业单投影之一：作业单页表格用的行形状。
 */
public interface JobOrderRow {

    Long getId();

    String getOrderNo();

    Long getPlotId();

    Long getDroneId();

    Long getPilotId();

    String getFormula();

    BigDecimal getDosePerMu();

    BigDecimal getPlannedMu();

    LocalDate getJobDate();

    String getWeather();

    String getStatus();

    BigDecimal getActualMu();

    LocalDateTime getFinishedAt();
}
