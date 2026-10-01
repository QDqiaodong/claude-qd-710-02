package com.drone.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.Plot;

/**
 * 地块仓储。
 *
 * <p>数据访问主线是【动态投影】：方法声明里不写死返回形状，
 * 形状由调用方在最后一参数 {@code Class<T> type} 上传入（比如
 * {@code PlotSummary} / {@code PlotDetailView} / {@code PlotOption}），
 * 于是一个 {@link #findByStatusOrderByPlotNoAsc(String, Class)} 就能喂给地图卡片、
 * 详情抽屉和下拉框三种完全不同的字段组合。
 */
public interface PlotRepository extends JpaRepository<Plot, Long> {

    /** 同一个查询方法，调用时决定要哪种投影 */
    <T> List<T> findByStatusOrderByPlotNoAsc(String status, Class<T> type);

    /** 无状态筛选时的全量查询，同样是动态投影 */
    <T> List<T> findAllByOrderByPlotNoAsc(Class<T> type);

    /** 按作物类型查（作物带筛选时用），动态投影 */
    <T> List<T> findByCropTypeOrderByPlotNoAsc(String cropType, Class<T> type);

    /** 单条详情：按地块编号命中，动态投影 */
    <T> List<T> findByPlotNoOrderByPlotNoAsc(String plotNo, Class<T> type);

    boolean existsByPlotNo(String plotNo);

    long countByStatus(String status);

    long countByCropType(String cropType);
}
