package com.drone.plant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.AbnormalHandling;

/** 异常架次停飞处置仓储。 */
public interface AbnormalHandlingRepository extends JpaRepository<AbnormalHandling, Long> {

    <T> List<T> findAllByOrderByRegisteredAtDesc(Class<T> type);

    <T> List<T> findByStatusOrderByRegisteredAtDesc(String status, Class<T> type);

    <T> List<T> findByDroneIdOrderByRegisteredAtDesc(Long droneId, Class<T> type);

    <T> List<T> findByDroneIdAndStatusInOrderByRegisteredAtDesc(Long droneId, List<String> statuses, Class<T> type);

    Optional<AbnormalHandling> findByFlightLogId(Long flightLogId);

    boolean existsByDroneIdAndStatusIn(Long droneId, List<String> statuses);

    long countByDroneIdAndStatusIn(Long droneId, List<String> statuses);

    long countByStatusIn(List<String> statuses);

    long countByRegisteredAtAfter(java.time.LocalDateTime after);
}
