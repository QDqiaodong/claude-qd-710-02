package com.drone.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.Drone;

/**
 * 无人机仓储：同样用动态投影，
 * {@code DroneCard}（卡片墙）/ {@code DroneOption}（下拉框）/ {@code DronePayload}（算药量）共用同一批查询方法。
 */
public interface DroneRepository extends JpaRepository<Drone, Long> {

    <T> List<T> findByStatusOrderByBodyNoAsc(String status, Class<T> type);

    <T> List<T> findAllByOrderByBodyNoAsc(Class<T> type);

    <T> List<T> findByDroneTypeOrderByTotalAreaMuDesc(String droneType, Class<T> type);

    <T> List<T> findByBodyNoOrderByBodyNoAsc(String bodyNo, Class<T> type);

    boolean existsByBodyNo(String bodyNo);

    long countByStatus(String status);
}
