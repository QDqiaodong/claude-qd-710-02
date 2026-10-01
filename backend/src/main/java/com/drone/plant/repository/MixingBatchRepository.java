package com.drone.plant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.drone.plant.entity.MixingBatch;

import jakarta.persistence.LockModeType;

/**
 * 配药批次仓储。
 *
 * <p>并发锁定的关键在 {@link #findByIdForUpdate}：
 * 两名复核人同时确认同一批次时，行级写锁把两人的“读状态→改状态”串成先后两步，
 * 后到的事务拿到锁后重新读到 LOCKED，只能得到“已被处理”；再由
 * uk_one_locked_per_order 唯一索引做数据库层兜底，刷新或重试都不会产生第二条锁定记录。
 */
public interface MixingBatchRepository extends JpaRepository<MixingBatch, Long> {

    List<MixingBatch> findByStatusOrderByUpdatedAtDesc(String status);

    List<MixingBatch> findAllByOrderByUpdatedAtDesc();

    List<MixingBatch> findByJobOrderIdOrderByIdDesc(Long jobOrderId);

    /** 未失效的批次（草稿 / 待复核 / 已驳回 / 已锁定），按业务约束一作业单至多一条 */
    Optional<MixingBatch> findFirstByJobOrderIdAndStatusNotOrderByIdDesc(Long jobOrderId, String status);

    Optional<MixingBatch> findByBatchNo(String batchNo);

    long countByStatus(String status);

    long countByJobOrderIdAndStatus(Long jobOrderId, String status);

    long countByJobOrderIdAndStatusNot(Long jobOrderId, String status);

    /** SELECT ... FOR UPDATE：锁定 / 失效联动时先把批次行锁住 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from MixingBatch b where b.id = :id")
    Optional<MixingBatch> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
