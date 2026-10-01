package com.drone.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.AbnormalAction;

/** 异常处置留痕仓储。 */
public interface AbnormalActionRepository extends JpaRepository<AbnormalAction, Long> {

    List<AbnormalAction> findByHandlingIdOrderByIdAsc(Long handlingId);
}
