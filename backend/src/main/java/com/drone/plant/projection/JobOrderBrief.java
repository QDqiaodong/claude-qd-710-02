package com.drone.plant.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 作业单投影之二：地块抽屉里的简报形状（只看得见进度，不带药剂细节）。
 */
public interface JobOrderBrief {

    Long getId();

    String getOrderNo();

    Long getPlotId();

    LocalDate getJobDate();

    String getStatus();

    BigDecimal getPlannedMu();

    BigDecimal getActualMu();

    Long getDroneId();

    Long getPilotId();

    LocalDateTime getFinishedAt();
}
