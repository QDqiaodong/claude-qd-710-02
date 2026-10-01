package com.drone.plant.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 模块六：配药复核留痕。SUBMIT 提交 / REJECT 驳回 / LOCK 锁定 / EXPIRE 系统失效，全部留一条。 */
@Data
@Entity
@Table(name = "mixing_review")
public class MixingReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /** SUBMIT / REJECT / LOCK / EXPIRE */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    /** 操作飞手；系统联动失效时为空 */
    @Column(name = "actor_pilot_id")
    private Long actorPilotId;

    /** 操作人姓名快照，操作后飞手离队也保留得住 */
    @Column(name = "actor_name", length = 32)
    private String actorName;

    /** 驳回原因 / 失效原因 / 锁定意见 */
    @Column(name = "comment", length = 255)
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
