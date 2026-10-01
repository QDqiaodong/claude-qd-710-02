package com.drone.plant.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 模块二：植保无人机台账。 */
@Data
@Entity
@Table(name = "drone")
public class Drone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 机身编号，如 DP-01 */
    @Column(name = "body_no", nullable = false, length = 16, unique = true)
    private String bodyNo;

    /** 型号 */
    @Column(name = "model", nullable = false, length = 32)
    private String model;

    /** 类型：MULTI 多旋翼 / SINGLE 单旋翼 / SPRAY 植保专用机 */
    @Column(name = "drone_type", nullable = false, length = 16)
    private String droneType;

    /** 最大载荷（L） */
    @Column(name = "max_payload", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxPayload;

    /** 喷幅（m） */
    @Column(name = "spray_width", nullable = false, precision = 5, scale = 2)
    private BigDecimal sprayWidth;

    /** 累计飞行小时 */
    @Column(name = "total_flight_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalFlightHours;

    /** 累计作业亩数 */
    @Column(name = "total_area_mu", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAreaMu;

    /** 电池组数 */
    @Column(name = "battery_packs", nullable = false)
    private Integer batteryPacks;

    /** 状态：IDLE 在库 / WORKING 作业中 / MAINT 维修 / DISABLED 停用 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
