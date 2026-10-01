package com.drone.plant.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 模块六：配药分桶明细（一桶一行，人工可调整，但每桶 > 0、不超载、合计守恒）。 */
@Data
@Entity
@Table(name = "mixing_bucket")
public class MixingBucket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /** 桶序号，从 1 开始 */
    @Column(name = "seq_no", nullable = false)
    private Integer seqNo;

    /** 本桶药量（L） */
    @Column(name = "amount_l", nullable = false, precision = 8, scale = 2)
    private BigDecimal amountL;
}
