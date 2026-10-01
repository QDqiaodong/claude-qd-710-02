package com.drone.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.MixingBucket;

/** 分桶明细仓储。 */
public interface MixingBucketRepository extends JpaRepository<MixingBucket, Long> {

    List<MixingBucket> findByBatchIdOrderBySeqNoAsc(Long batchId);

    void deleteByBatchId(Long batchId);

    long countByBatchId(Long batchId);
}
