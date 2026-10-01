package com.drone.plant.projection;

import java.math.BigDecimal;

/**
 * 地块投影之三：派工时下拉框用的瘦身形状（只带编号、承包户、面积、作物）。
 */
public interface PlotOption {

    Long getId();

    String getPlotNo();

    String getFarmerName();

    String getCropType();

    BigDecimal getAreaMu();
}
