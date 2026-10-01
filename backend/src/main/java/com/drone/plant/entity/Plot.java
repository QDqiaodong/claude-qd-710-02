package com.drone.plant.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 模块一：地块台账（村组承包地块，作业地图上的一个多边形）。 */
@Data
@Entity
@Table(name = "plot")
public class Plot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 地块编号，如 P-01 */
    @Column(name = "plot_no", nullable = false, length = 16, unique = true)
    private String plotNo;

    /** 承包农户 / 合作社 */
    @Column(name = "farmer_name", nullable = false, length = 64)
    private String farmerName;

    /** 作物类型：RICE 水稻 / WHEAT 小麦 / CORN 玉米 / COTTON 棉花 / FRUIT 果树 / OILSEED 油菜 */
    @Column(name = "crop_type", nullable = false, length = 16)
    private String cropType;

    /** 面积（亩） */
    @Column(name = "area_mu", nullable = false, precision = 10, scale = 2)
    private BigDecimal areaMu;

    /** 所属村组 */
    @Column(name = "village_group", nullable = false, length = 64)
    private String villageGroup;

    /** 地形：PLAIN 平原 / HILL 丘陵 / MOUNTAIN 山地 */
    @Column(name = "terrain", nullable = false, length = 16)
    private String terrain;

    /** 灌溉条件：DITCH 沟灌 / SPRINKLER 喷灌 / DRIP 滴灌 / RAINFED 雨养 */
    @Column(name = "irrigation", nullable = false, length = 16)
    private String irrigation;

    /** 状态：PENDING 待作业 / WORKING 作业中 / DONE 已完成 / HOLD 暂缓 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
