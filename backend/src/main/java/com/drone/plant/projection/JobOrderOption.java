package com.drone.plant.projection;

import java.time.LocalDate;

/**
 * 作业单投影之三：飞行日志页顶部「选择作业单」下拉框用的瘦身形状。
 */
public interface JobOrderOption {

    Long getId();

    String getOrderNo();

    Long getPlotId();

    LocalDate getJobDate();

    String getStatus();
}
