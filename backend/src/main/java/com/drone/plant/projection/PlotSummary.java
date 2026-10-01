package com.drone.plant.projection;

import java.math.BigDecimal;

/**
 * 地块投影之一：作业地图左侧列表用的卡片形状。
 * 与 {@link PlotOption}、{@link PlotDetailView} 共用同一个查询方法，形状由调用时决定。
 */
public interface PlotSummary {

    Long getId();

    String getPlotNo();

    String getFarmerName();

    String getCropType();

    BigDecimal getAreaMu();

    String getVillageGroup();

    String getTerrain();

    String getIrrigation();

    String getStatus();
}
