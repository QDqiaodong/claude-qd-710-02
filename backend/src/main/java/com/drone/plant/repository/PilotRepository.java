package com.drone.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.Pilot;

/** 飞手仓储：动态投影视形状取自 {@code PilotCard} / {@code PilotOption}。 */
public interface PilotRepository extends JpaRepository<Pilot, Long> {

    <T> List<T> findByStatusOrderByPilotNoAsc(String status, Class<T> type);

    <T> List<T> findAllByOrderByPilotNoAsc(Class<T> type);

    <T> List<T> findByLicenseLevelOrderByTotalSortsDesc(String licenseLevel, Class<T> type);

    boolean existsByPilotNo(String pilotNo);

    boolean existsByPhone(String phone);

    long countByStatus(String status);

    long countByLicenseLevel(String licenseLevel);
}
