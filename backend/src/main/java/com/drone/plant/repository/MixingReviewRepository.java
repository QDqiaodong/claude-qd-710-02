package com.drone.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.MixingReview;

/** 复核留痕仓储，按 id 升序即时间线顺序。 */
public interface MixingReviewRepository extends JpaRepository<MixingReview, Long> {

    List<MixingReview> findByBatchIdOrderByIdAsc(Long batchId);
}
