package com.drone.plant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drone.plant.common.BizException;
import com.drone.plant.dto.PlotDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.Plot;
import com.drone.plant.projection.DroneOption;
import com.drone.plant.projection.JobOrderBrief;
import com.drone.plant.projection.PilotOption;
import com.drone.plant.projection.PlotDetailView;
import com.drone.plant.projection.PlotOption;
import com.drone.plant.projection.PlotSummary;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.FlightLogRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;
import com.drone.plant.support.PlantRules;

/**
 * 模块一：地块。
 *
 * <p>查询一律走 {@link PlotRepository} 的动态投影方法：
 * {@code PlotSummary}（列表卡片）/ {@code PlotDetailView}（抽屉详情）/ {@code PlotOption}（派工下拉框）
 * 三种形状由调用点在最后一参数传入，仓储层不感知返回形状。
 */
@Service
public class PlotService {

    private final PlotRepository plotRepository;
    private final JobOrderRepository jobOrderRepository;
    private final FlightLogRepository flightLogRepository;
    private final DroneRepository droneRepository;
    private final PilotRepository pilotRepository;

    public PlotService(PlotRepository plotRepository, JobOrderRepository jobOrderRepository,
                       FlightLogRepository flightLogRepository, DroneRepository droneRepository,
                       PilotRepository pilotRepository) {
        this.plotRepository = plotRepository;
        this.jobOrderRepository = jobOrderRepository;
        this.flightLogRepository = flightLogRepository;
        this.droneRepository = droneRepository;
        this.pilotRepository = pilotRepository;
    }

    /** 列表：状态 / 作物类型两个筛选各自命中不同的动态投影查询方法 */
    @Transactional(readOnly = true)
    public List<Views.PlotView> list(String status, String cropType) {
        List<PlotSummary> rows;
        if (isBlank(status)) {
            rows = isBlank(cropType)
                    ? plotRepository.findAllByOrderByPlotNoAsc(PlotSummary.class)
                    : plotRepository.findByCropTypeOrderByPlotNoAsc(cropType, PlotSummary.class);
        } else {
            rows = plotRepository.findByStatusOrderByPlotNoAsc(status, PlotSummary.class);
        }
        Map<Long, PlotStat> stats = plotStats();
        List<Views.PlotView> list = new ArrayList<>();
        for (PlotSummary row : rows) {
            list.add(toView(row, stats.get(row.getId())));
        }
        return list;
    }

    /** 详情：单号定位 + 详情投影，外挂该地块名下的作业单列表 */
    @Transactional(readOnly = true)
    public Views.PlotDetail detail(Long id) {
        Plot plot = plotRepository.findById(id)
                .orElseThrow(() -> new BizException("地块不存在：id=" + id));
        List<PlotDetailView> rows =
                plotRepository.findByPlotNoOrderByPlotNoAsc(plot.getPlotNo(), PlotDetailView.class);
        if (rows.isEmpty()) {
            throw new BizException("地块 " + plot.getPlotNo() + " 的详情数据缺失");
        }
        PlotDetailView row = rows.get(0);
        PlotStat stat = plotStats().get(row.getId());
        Map<Long, String> droneName = droneNameMap();
        Map<Long, String> pilotName = pilotNameMap();
        List<Views.PlotOrderItem> orders = new ArrayList<>();
        for (JobOrderBrief order : jobOrderRepository.findByPlotIdOrderByJobDateDesc(id, JobOrderBrief.class)) {
            orders.add(orderItem(order, droneName, pilotName));
        }
        return new Views.PlotDetail(toDetailView(row, stat), orders);
    }

    /**
     * 派工下拉框：同一个仓储方法换成 {@link PlotOption} 的形状，
     * 只拿编号 / 承包户 / 作物 / 面积这几列。
     */
    @Transactional(readOnly = true)
    public List<Views.PlotOptionView> options() {
        List<Views.PlotOptionView> list = new ArrayList<>();
        for (String status : List.of("PENDING", "WORKING")) {
            for (PlotOption row : plotRepository.findByStatusOrderByPlotNoAsc(status, PlotOption.class)) {
                list.add(new Views.PlotOptionView(row.getId(), row.getPlotNo(), row.getFarmerName(),
                        PlantRules.cropName(row.getCropType()), row.getAreaMu(),
                        status, PlantRules.plotStatusName(status)));
            }
        }
        return list;
    }

    @Transactional
    public Views.PlotView create(PlotDtos.SaveReq req) {
        String plotNo = trim(req.getPlotNo());
        if (plotNo.isEmpty()) {
            throw new BizException("地块编号不能为空");
        }
        if (plotRepository.existsByPlotNo(plotNo)) {
            throw new BizException("地块编号 " + plotNo + " 已存在");
        }
        String cropType = upper(req.getCropType());
        if (!PlantRules.isValidCropType(cropType)) {
            throw new BizException("作物类型只能是 RICE / WHEAT / CORN / COTTON / FRUIT / OILSEED，收到的是：" + req.getCropType());
        }
        String terrain = upper(req.getTerrain());
        if (!PlantRules.isValidTerrain(terrain)) {
            throw new BizException("地形只能是 PLAIN / HILL / MOUNTAIN，收到的是：" + req.getTerrain());
        }
        String irrigation = upper(req.getIrrigation());
        if (!PlantRules.isValidIrrigation(irrigation)) {
            throw new BizException("灌溉条件只能是 DITCH / SPRINKLER / DRIP / RAINFED，收到的是：" + req.getIrrigation());
        }
        BigDecimal areaMu = req.getAreaMu() == null ? BigDecimal.ZERO : req.getAreaMu();
        if (areaMu.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("地块面积必须大于 0");
        }
        String farmer = trim(req.getFarmerName());
        if (farmer.isEmpty()) {
            throw new BizException("承包农户 / 合作社不能为空");
        }
        Plot plot = new Plot();
        plot.setPlotNo(plotNo);
        plot.setFarmerName(farmer);
        plot.setCropType(cropType);
        plot.setAreaMu(areaMu.setScale(2, RoundingMode.HALF_UP));
        plot.setVillageGroup(trim(req.getVillageGroup()));
        plot.setTerrain(terrain);
        plot.setIrrigation(irrigation);
        plot.setStatus("PENDING");
        plot.setUpdatedAt(LocalDateTime.now());
        return toView(plotRepository.save(plot), null);
    }

    /** 地块状态：待作业 ↔ 暂缓可手工改，作业中 / 已完成由作业单流转自动推进 */
    @Transactional
    public Views.PlotView updateStatus(Long id, String status) {
        Plot plot = plotRepository.findById(id)
                .orElseThrow(() -> new BizException("地块不存在：id=" + id));
        String target = upper(status);
        if (!PlantRules.isValidPlotStatus(target)) {
            throw new BizException("地块状态只能是 PENDING / WORKING / DONE / HOLD，收到的是：" + status);
        }
        if ("WORKING".equals(target) || "DONE".equals(target)) {
            throw new BizException("作业中 / 已完成由作业单流转自动推进，请到作业单页操作");
        }
        if ("HOLD".equals(target) && jobOrderRepository.countByPlotIdAndStatus(id, "WORKING") > 0) {
            throw new BizException("地块 " + plot.getPlotNo() + " 还有作业中的作业单，不能暂缓");
        }
        plot.setStatus(target);
        plot.setUpdatedAt(LocalDateTime.now());
        return toView(plotRepository.save(plot), plotStats().get(id));
    }

    // ---------------- 内部工具 ----------------

    /** 每个地块的作业汇总：作业单总数、累计已完成亩数、最近作业日期 */
    private Map<Long, PlotStat> plotStats() {
        Map<Long, PlotStat> map = new HashMap<>();
        for (JobOrderBrief order : jobOrderRepository.findAllByOrderByJobDateDesc(JobOrderBrief.class)) {
            Long plotId = order.getPlotId();
            if (plotId == null) {
                continue;
            }
            PlotStat old = map.get(plotId);
            long count = old == null ? 1 : old.count + 1;
            BigDecimal done = (old == null ? BigDecimal.ZERO : old.doneMu)
                    .add("DONE".equals(order.getStatus()) ? nvl(order.getActualMu()) : BigDecimal.ZERO);
            LocalDate last = old == null ? null : old.lastJobDate;
            if (order.getJobDate() != null && (last == null || order.getJobDate().isAfter(last))) {
                last = order.getJobDate();
            }
            if (order.getJobDate() == null && old != null) {
                last = old.lastJobDate;
            }
            map.put(plotId, new PlotStat(count, done, last));
        }
        return map;
    }

    private Map<Long, String> droneNameMap() {
        Map<Long, String> map = new HashMap<>();
        for (DroneOption row : droneRepository.findAllByOrderByBodyNoAsc(DroneOption.class)) {
            map.put(row.getId(), row.getBodyNo());
        }
        return map;
    }

    private Map<Long, String> pilotNameMap() {
        Map<Long, String> map = new HashMap<>();
        for (PilotOption row : pilotRepository.findAllByOrderByPilotNoAsc(PilotOption.class)) {
            map.put(row.getId(), row.getName());
        }
        return map;
    }

    private Views.PlotOrderItem orderItem(JobOrderBrief order, Map<Long, String> droneName,
                                          Map<Long, String> pilotName) {
        return new Views.PlotOrderItem(order.getId(), order.getOrderNo(), order.getJobDate(), order.getStatus(),
                PlantRules.orderStatusName(order.getStatus()), nvl(order.getPlannedMu()), nvl(order.getActualMu()),
                droneName.get(order.getDroneId()), pilotName.get(order.getPilotId()),
                (int) flightLogRepository.countByJobOrderId(order.getId()), order.getFinishedAt());
    }

    private Views.PlotView toView(Plot plot, PlotStat stat) {
        return new Views.PlotView(plot.getId(), plot.getPlotNo(), plot.getFarmerName(), plot.getCropType(),
                PlantRules.cropName(plot.getCropType()), plot.getAreaMu(), plot.getVillageGroup(), plot.getTerrain(),
                PlantRules.terrainName(plot.getTerrain()), plot.getIrrigation(),
                PlantRules.irrigationName(plot.getIrrigation()), plot.getStatus(),
                PlantRules.plotStatusName(plot.getStatus()), stat == null ? 0 : stat.count,
                stat == null ? BigDecimal.ZERO : stat.doneMu, stat == null ? null : stat.lastJobDate,
                plot.getUpdatedAt());
    }

    private Views.PlotView toView(PlotSummary row, PlotStat stat) {
        return new Views.PlotView(row.getId(), row.getPlotNo(), row.getFarmerName(), row.getCropType(),
                PlantRules.cropName(row.getCropType()), row.getAreaMu(), row.getVillageGroup(), row.getTerrain(),
                PlantRules.terrainName(row.getTerrain()), row.getIrrigation(),
                PlantRules.irrigationName(row.getIrrigation()), row.getStatus(),
                PlantRules.plotStatusName(row.getStatus()), stat == null ? 0 : stat.count,
                stat == null ? BigDecimal.ZERO : stat.doneMu, stat == null ? null : stat.lastJobDate, null);
    }

    private Views.PlotView toDetailView(PlotDetailView row, PlotStat stat) {
        return new Views.PlotView(row.getId(), row.getPlotNo(), row.getFarmerName(), row.getCropType(),
                PlantRules.cropName(row.getCropType()), row.getAreaMu(), row.getVillageGroup(), row.getTerrain(),
                PlantRules.terrainName(row.getTerrain()), row.getIrrigation(),
                PlantRules.irrigationName(row.getIrrigation()), row.getStatus(),
                PlantRules.plotStatusName(row.getStatus()), stat == null ? 0 : stat.count,
                stat == null ? BigDecimal.ZERO : stat.doneMu, stat == null ? null : stat.lastJobDate,
                row.getUpdatedAt());
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 地块侧的作业汇总小计 */
    private record PlotStat(long count, BigDecimal doneMu, LocalDate lastJobDate) {
    }
}
