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

/**
 * 模块六：作业前配药批次。
 *
 * <p>状态机：DRAFT 草稿 → PENDING_REVIEW 待复核 → LOCKED 已锁定；
 * 待复核可被 REJECTED 驳回（退回草稿态修改后再次提交）；
 * 作业单取消、改派或计划口径变化时所有未失效批次立即 EXPIRED 已失效（终态，不可恢复）。
 *
 * <p>发起批次时把作业单号、配方、计划亩数、亩用量、无人机与载荷全部拍快照，
 * 复核依据与起飞一致性校验都以快照为准，不回查会变化的现表。
 */
@Data
@Entity
@Table(name = "mixing_batch")
public class MixingBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配药批次号，如 PB-24092201 */
    @Column(name = "batch_no", nullable = false, length = 28, unique = true)
    private String batchNo;

    @Column(name = "job_order_id", nullable = false)
    private Long jobOrderId;

    /** DRAFT / PENDING_REVIEW / LOCKED / REJECTED / EXPIRED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "prepared_pilot_id", nullable = false)
    private Long preparedPilotId;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    // ---------------- 发起时快照 ----------------

    @Column(name = "order_no_snapshot", nullable = false, length = 24)
    private String orderNoSnapshot;

    @Column(name = "formula_snapshot", nullable = false, length = 128)
    private String formulaSnapshot;

    @Column(name = "planned_mu_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal plannedMuSnapshot;

    @Column(name = "dose_per_mu_snapshot", nullable = false, precision = 5, scale = 2)
    private BigDecimal dosePerMuSnapshot;

    /** 计划总药量（L）= 计划亩数 × 亩用量 */
    @Column(name = "total_l", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalL;

    @Column(name = "drone_id_snapshot", nullable = false)
    private Long droneIdSnapshot;

    @Column(name = "drone_body_no_snapshot", nullable = false, length = 16)
    private String droneBodyNoSnapshot;

    @Column(name = "drone_model_snapshot", nullable = false, length = 32)
    private String droneModelSnapshot;

    @Column(name = "max_payload_snapshot", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxPayloadSnapshot;

    @Column(name = "bucket_count", nullable = false)
    private Integer bucketCount;

    @Column(name = "expire_reason", length = 255)
    private String expireReason;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 数据库生成列：LOCKED → 1，其余 → NULL，配合唯一索引保证一单一锁定。
     * insertable/updatable=false：值完全由 MySQL 维护，JPA 不写入。
     */
    @Column(name = "status_lock_key", insertable = false, updatable = false)
    private Integer statusLockKey;

    /** 数据库生成列：未失效 → 1，EXPIRED → NULL，配合唯一索引保证一作业单同时只有一个有效批次。 */
    @Column(name = "active_batch_key", insertable = false, updatable = false)
    private Integer activeBatchKey;
}
