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

/** 模块三：飞手台账。 */
@Data
@Entity
@Table(name = "pilot")
public class Pilot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 飞手编号，如 PL-01 */
    @Column(name = "pilot_no", nullable = false, length = 16, unique = true)
    private String pilotNo;

    @Column(name = "name", nullable = false, length = 32)
    private String name;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    /** 执照等级：LEVEL1 初级 / LEVEL2 中级 / LEVEL3 高级 / INSTRUCTOR 教员 */
    @Column(name = "license_level", nullable = false, length = 16)
    private String licenseLevel;

    /** 累计架次 */
    @Column(name = "total_sorts", nullable = false)
    private Integer totalSorts;

    /** 累计作业亩数 */
    @Column(name = "total_area_mu", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAreaMu;

    /** 状态：ACTIVE 在队 / LEAVE 请假 / RESIGNED 离职 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
