package com.drone.plant.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 模块五-延：异常架次停飞处置单。
 *
 * <p>一条异常架次（flight_log）最多一条处置：登记异常架次时自动立案进入 PENDING；
 * 值班主管可继续观察 OBSERVING、停飞机身 GROUNDED、解除 CLOSED（终态）。
 * GROUNDED 期间关联无人机为 MAINT，CLOSED 且该机身没有其他未结束处置时才允许回到 IDLE。
 */
@Data
@Entity
@Table(name = "abnormal_handling")
public class AbnormalHandling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 处置单号，如 AH-260923-01 */
    @Column(name = "handling_no", nullable = false, length = 24, unique = true)
    private String handlingNo;

    /** 异常架次（飞行日志 id），一条异常架次最多一条处置 */
    @Column(name = "flight_log_id", nullable = false, unique = true)
    private Long flightLogId;

    /** 所属作业单（立案时快照） */
    @Column(name = "job_order_id", nullable = false)
    private Long jobOrderId;

    /** 关联无人机（立案时快照） */
    @Column(name = "drone_id", nullable = false)
    private Long droneId;

    /** PENDING 待处置 / OBSERVING 继续观察 / GROUNDED 停飞中 / CLOSED 已解除 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 立案飞手；系统随架次登记自动立案时为空 */
    @Column(name = "register_pilot_id")
    private Long registerPilotId;

    @Column(name = "register_name", length = 32)
    private String registerName;

    /** 最近一次处置的值班主管 */
    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Column(name = "supervisor_name", length = 32)
    private String supervisorName;

    /** 处置结论：解除前必填 */
    @Column(name = "conclusion", length = 255)
    private String conclusion;

    /** 继续观察意见 */
    @Column(name = "observe_comment", length = 255)
    private String observeComment;

    /** 停飞意见 */
    @Column(name = "ground_comment", length = 255)
    private String groundComment;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "observed_at")
    private LocalDateTime observedAt;

    @Column(name = "grounded_at")
    private LocalDateTime groundedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
