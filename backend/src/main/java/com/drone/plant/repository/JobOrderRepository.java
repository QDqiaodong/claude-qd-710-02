package com.drone.plant.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.JobOrder;

/**
 * 作业单仓储：动态投影的核心演示点。
 * {@link #findByStatusOrderByJobDateDesc(String, Class)} 这一个方法，
 * 表格页传 {@code JobOrderRow}、下拉框传 {@code JobOrderOption}、抽屉摘要传 {@code JobOrderBrief}。
 */
public interface JobOrderRepository extends JpaRepository<JobOrder, Long> {

    <T> List<T> findByStatusOrderByJobDateDesc(String status, Class<T> type);

    <T> List<T> findAllByOrderByJobDateDesc(Class<T> type);

    <T> List<T> findByPlotIdOrderByJobDateDesc(Long plotId, Class<T> type);

    <T> List<T> findByJobDateBetweenOrderByJobDateDesc(LocalDate from, LocalDate to, Class<T> type);

    <T> List<T> findByDroneIdOrderByJobDateDesc(Long droneId, Class<T> type);

    long countByDroneIdAndStatus(Long droneId, String status);

    boolean existsByOrderNo(String orderNo);

    long countByStatus(String status);

    long countByPilotId(Long pilotId);

    long countByPlotIdAndStatus(Long plotId, String status);
}
