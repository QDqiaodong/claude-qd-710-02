package com.drone.plant.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 地块投影之二：抽屉里的地块详情形状（比列表卡片多出书签与更新时间）。
 */
public interface PlotDetailView {

    Long getId();

    String getPlotNo();

    String getFarmerName();

    String getCropType();

    BigDecimal getAreaMu();

    String getVillageGroup();

    String getTerrain();

    String getIrrigation();

    String getStatus();

    LocalDateTime getUpdatedAt();
}
