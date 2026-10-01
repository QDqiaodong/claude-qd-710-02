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

/** 模块五：飞行日志（作业单下的每一架次）。 */
@Data
@Entity
@Table(name = "flight_log")
public class FlightLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_order_id", nullable = false)
    private Long jobOrderId;

    /** 架次序号，同一作业单内自增 */
    @Column(name = "seq_no", nullable = false)
    private Integer seqNo;

    @Column(name = "takeoff_at", nullable = false)
    private LocalDateTime takeoffAt;

    @Column(name = "landing_at")
    private LocalDateTime landingAt;

    /** 飞行时长（分钟） */
    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    /** 本架次作业亩数 */
    @Column(name = "area_mu", nullable = false, precision = 10, scale = 2)
    private BigDecimal areaMu;

    /** 消耗药量（L） */
    @Column(name = "chemical_l", nullable = false, precision = 10, scale = 2)
    private BigDecimal chemicalL;

    /** 风速（m/s） */
    @Column(name = "wind_speed", precision = 4, scale = 1)
    private BigDecimal windSpeed;

    /** 气温 */
    @Column(name = "temperature", precision = 4, scale = 1)
    private BigDecimal temperature;

    /** 是否异常 */
    @Column(name = "abnormal", nullable = false)
    private Boolean abnormal;

    @Column(name = "abnormal_desc", length = 255)
    private String abnormalDesc;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
