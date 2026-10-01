package com.drone.plant.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 模块五-延：异常处置留痕。REGISTER 自动立案 / OBSERVE 继续观察 / GROUND 停飞 / RELEASE 解除，全部留一条。 */
@Data
@Entity
@Table(name = "abnormal_action")
public class AbnormalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handling_id", nullable = false)
    private Long handlingId;

    /** REGISTER / OBSERVE / GROUND / RELEASE */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    /** 操作飞手；系统自动立案时为空 */
    @Column(name = "actor_pilot_id")
    private Long actorPilotId;

    /** 操作人姓名快照 */
    @Column(name = "actor_name", length = 32)
    private String actorName;

    /** 处置意见 / 结论 */
    @Column(name = "comment", length = 255)
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
