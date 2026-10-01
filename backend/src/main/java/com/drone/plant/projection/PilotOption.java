package com.drone.plant.projection;

/**
 * 飞手投影之二：下拉框用的瘦身形状（编号 + 姓名 + 执照等级）。
 */
public interface PilotOption {

    Long getId();

    String getPilotNo();

    String getName();

    String getLicenseLevel();

    String getStatus();
}
