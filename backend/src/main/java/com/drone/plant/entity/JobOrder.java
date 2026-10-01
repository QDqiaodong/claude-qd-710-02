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

/** 模块四：作业单（一次喷洒作业的派工凭据）。 */
@Data
@Entity
@Table(name = "job_order")
public class JobOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 作业单号，如 JO-24091801 */
    @Column(name = "order_no", nullable = false, length = 24, unique = true)
    private String orderNo;

    @Column(name = "plot_id", nullable = false)
    private Long plotId;

    @Column(name = "drone_id", nullable = false)
    private Long droneId;

    @Column(name = "pilot_id", nullable = false)
    private Long pilotId;

    /** 药剂配方 */
    @Column(name = "formula", nullable = false, length = 128)
    private String formula;

    /** 亩用量（L/亩） */
    @Column(name = "dose_per_mu", nullable = false, precision = 5, scale = 2)
    private BigDecimal dosePerMu;

    /** 计划作业亩数 */
    @Column(name = "planned_mu", nullable = false, precision = 10, scale = 2)
    private BigDecimal plannedMu;

    @Column(name = "job_date", nullable = false)
    private LocalDate jobDate;

    /** 天气 */
    @Column(name = "weather", length = 16)
    private String weather;

    /** 状态：PENDING 待飞 / WORKING 作业中 / DONE 已完成 / CANCELED 已取消 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 实际作业亩数（由飞行日志累加） */
    @Column(name = "actual_mu", nullable = false, precision = 10, scale = 2)
    private BigDecimal actualMu;

    /** 完成时间 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
