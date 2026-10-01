package com.drone.plant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drone.plant.common.BizException;
import com.drone.plant.dto.FlightLogDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.AbnormalHandling;
import com.drone.plant.entity.Drone;
import com.drone.plant.entity.FlightLog;
import com.drone.plant.entity.JobOrder;
import com.drone.plant.entity.Pilot;
import com.drone.plant.projection.DroneOption;
import com.drone.plant.projection.FlightLogRow;
import com.drone.plant.projection.FlightLogStat;
import com.drone.plant.projection.JobOrderBrief;
import com.drone.plant.projection.PilotOption;
import com.drone.plant.projection.PlotOption;
import com.drone.plant.repository.AbnormalHandlingRepository;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.FlightLogRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;
import com.drone.plant.support.PlantRules;

/**
 * 模块五：飞行日志（作业单下的每一架次）。
 *
 * <p>{@code FlightLogRow}（表格行）/ {@code FlightLogStat}（统计条）共用同一批动态投影方法。
 * 每登记一架次，就把亩数、飞行小时回填到无人机台账，把架次与亩数回填到飞手台账。
 */
@Service
public class FlightLogService {

    private final FlightLogRepository flightLogRepository;
    private final JobOrderRepository jobOrderRepository;
    private final DroneRepository droneRepository;
    private final PilotRepository pilotRepository;
    private final PlotRepository plotRepository;
    private final AbnormalHandlingRepository handlingRepository;
    private final AbnormalHandlingService handlingService;

    public FlightLogService(FlightLogRepository flightLogRepository, JobOrderRepository jobOrderRepository,
                            DroneRepository droneRepository, PilotRepository pilotRepository,
                            PlotRepository plotRepository, AbnormalHandlingRepository handlingRepository,
                            AbnormalHandlingService handlingService) {
        this.flightLogRepository = flightLogRepository;
        this.jobOrderRepository = jobOrderRepository;
        this.droneRepository = droneRepository;
        this.pilotRepository = pilotRepository;
        this.plotRepository = plotRepository;
        this.handlingRepository = handlingRepository;
        this.handlingService = handlingService;
    }

    @Transactional(readOnly = true)
    public List<Views.FlightLogView> list(Long jobOrderId, Long droneId) {
        List<FlightLogRow> rows = queryRows(jobOrderId, droneId, false);
        NameBook book = nameBook();
        List<Views.FlightLogView> list = new ArrayList<>();
        for (FlightLogRow row : rows) {
            list.add(toView(row, book));
        }
        return list;
    }

    /** 只看异常架次：飞行风险复核入口 */
    @Transactional(readOnly = true)
    public List<Views.FlightLogView> abnormal(Long jobOrderId, Long droneId) {
        List<FlightLogRow> rows = queryRows(jobOrderId, droneId, true);
        NameBook book = nameBook();
        List<Views.FlightLogView> list = new ArrayList<>();
        for (FlightLogRow row : rows) {
            list.add(toView(row, book));
        }
        return list;
    }

    /**
     * 按作业单 / 机身 / 是否异常取架次：
     * 按机身筛时先把该机身的作业单 id 算出来，再用 IN 一次取回（飞行日志不直接存 drone_id）。
     */
    private List<FlightLogRow> queryRows(Long jobOrderId, Long droneId, boolean onlyAbnormal) {
        if (droneId != null) {
            List<Long> orderIds = new ArrayList<>();
            for (JobOrderBrief order : jobOrderRepository.findByDroneIdOrderByJobDateDesc(droneId,
                    JobOrderBrief.class)) {
                if (jobOrderId == null || jobOrderId.equals(order.getId())) {
                    orderIds.add(order.getId());
                }
            }
            if (orderIds.isEmpty()) {
                return List.of();
            }
            return onlyAbnormal
                    ? flightLogRepository.findByJobOrderIdInAndAbnormalTrueOrderByTakeoffAtDesc(orderIds,
                            FlightLogRow.class)
                    : flightLogRepository.findByJobOrderIdInOrderByTakeoffAtDesc(orderIds, FlightLogRow.class);
        }
        if (jobOrderId != null) {
            return onlyAbnormal
                    ? flightLogRepository.findByJobOrderIdAndAbnormalTrueOrderBySeqNoAsc(jobOrderId,
                            FlightLogRow.class)
                    : flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(jobOrderId, FlightLogRow.class);
        }
        return onlyAbnormal
                ? flightLogRepository.findByAbnormalTrueOrderByTakeoffAtDesc(FlightLogRow.class)
                : flightLogRepository.findAllByOrderByTakeoffAtDesc(FlightLogRow.class);
    }

    /** 汇总条：总架次 / 总时长 / 总亩数 / 总药量 / 异常架次，全部走 {@link FlightLogStat} 投影 */
    @Transactional(readOnly = true)
    public Views.LogSummary summary(Long jobOrderId) {
        List<FlightLogStat> rows = jobOrderId == null
                ? flightLogRepository.findAllByOrderByTakeoffAtDesc(FlightLogStat.class)
                : flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(jobOrderId, FlightLogStat.class);
        long minutes = 0;
        long abnormal = 0;
        BigDecimal area = BigDecimal.ZERO;
        BigDecimal chemical = BigDecimal.ZERO;
        for (FlightLogStat row : rows) {
            minutes += row.getDurationMin() == null ? 0 : row.getDurationMin();
            area = area.add(nvl(row.getAreaMu()));
            chemical = chemical.add(nvl(row.getChemicalL()));
            if (Boolean.TRUE.equals(row.getAbnormal())) {
                abnormal++;
            }
        }
        return new Views.LogSummary(rows.size(), minutes, area.setScale(2, RoundingMode.HALF_UP),
                chemical.setScale(2, RoundingMode.HALF_UP), abnormal);
    }

    @Transactional
    public Views.FlightLogView create(FlightLogDtos.SaveReq req) {
        Long jobOrderId = req.getJobOrderId();
        if (jobOrderId == null) {
            throw new BizException("请先选择所属作业单");
        }
        JobOrder order = jobOrderRepository.findById(jobOrderId)
                .orElseThrow(() -> new BizException("作业单不存在：id=" + jobOrderId));
        if ("PENDING".equals(order.getStatus())) {
            throw new BizException("作业单 " + order.getOrderNo() + " 尚未起飞，先点「起飞」再登记架次");
        }
        if ("DONE".equals(order.getStatus())) {
            throw new BizException("作业单 " + order.getOrderNo() + " 已完成，不能再补登记架次");
        }
        if ("CANCELED".equals(order.getStatus())) {
            throw new BizException("作业单 " + order.getOrderNo() + " 已取消，不能登记架次");
        }
        if (req.getTakeoffAt() == null || req.getLandingAt() == null) {
            throw new BizException("起飞时间与降落时间都要填");
        }
        long minutes = PlantRules.minutesBetween(req.getTakeoffAt(), req.getLandingAt());
        if (minutes <= 0) {
            throw new BizException("降落时间必须晚于起飞时间");
        }
        if (minutes > PlantRules.MAX_SORT_MINUTES) {
            throw new BizException("单架次不得超过 " + PlantRules.MAX_SORT_MINUTES + " 分钟，本次是 " + minutes + " 分钟");
        }
        BigDecimal areaMu = nvl(req.getAreaMu());
        if (areaMu.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("本架次作业亩数必须大于 0");
        }
        if (areaMu.compareTo(PlantRules.MAX_SORT_AREA) > 0) {
            throw new BizException("单架次作业亩数不得超过 " + PlantRules.MAX_SORT_AREA + " 亩");
        }
        BigDecimal loggedArea = BigDecimal.ZERO;
        for (FlightLogStat stat : flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(jobOrderId,
                FlightLogStat.class)) {
            loggedArea = loggedArea.add(nvl(stat.getAreaMu()));
        }
        BigDecimal orderCeiling = nvl(order.getPlannedMu()).multiply(PlantRules.ACTUAL_TOLERANCE);
        if (loggedArea.add(areaMu).compareTo(orderCeiling) > 0) {
            throw new BizException("本作业单已累计 " + loggedArea + " 亩，再加这架次会超过计划亩数的 1.2 倍（上限 "
                    + orderCeiling.setScale(2, RoundingMode.HALF_UP) + " 亩）");
        }
        BigDecimal chemicalL = nvl(req.getChemicalL());
        if (chemicalL.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("消耗药量必须大于 0");
        }
        Drone drone = droneRepository.findById(order.getDroneId())
                .orElseThrow(() -> new BizException("作业单绑定的无人机不存在"));
        BigDecimal chemicalCeiling = nvl(drone.getMaxPayload()).multiply(PlantRules.ACTUAL_TOLERANCE);
        if (chemicalL.compareTo(chemicalCeiling) > 0) {
            throw new BizException("机身 " + drone.getBodyNo() + " 最大载荷 " + drone.getMaxPayload()
                    + " L，单架次药量上限 " + chemicalCeiling.setScale(2, RoundingMode.HALF_UP) + " L，收到的是 " + chemicalL);
        }
        BigDecimal windSpeed = nvl(req.getWindSpeed());
        boolean abnormal = req.getAbnormal() != null && req.getAbnormal();
        String desc = trim(req.getAbnormalDesc());
        if (windSpeed.compareTo(PlantRules.WARN_WIND) >= 0 && !abnormal) {
            throw new BizException("风速 " + windSpeed + " m/s 已达到 " + PlantRules.WARN_WIND
                    + " m/s 告警阈值，本架次必须标记为异常并写明情况");
        }
        if (abnormal && desc.isEmpty()) {
            throw new BizException("标记为异常的架次必须填写异常描述");
        }

        FlightLog log = new FlightLog();
        log.setJobOrderId(jobOrderId);
        log.setSeqNo((int) flightLogRepository.countByJobOrderId(jobOrderId) + 1);
        log.setTakeoffAt(req.getTakeoffAt());
        log.setLandingAt(req.getLandingAt());
        log.setDurationMin((int) minutes);
        log.setAreaMu(areaMu.setScale(2, RoundingMode.HALF_UP));
        log.setChemicalL(chemicalL.setScale(2, RoundingMode.HALF_UP));
        log.setWindSpeed(windSpeed);
        log.setTemperature(req.getTemperature());
        log.setAbnormal(abnormal);
        log.setAbnormalDesc(abnormal ? desc : null);
        log.setCreatedAt(LocalDateTime.now());
        FlightLog saved = flightLogRepository.save(log);

        // 回填作业单实际亩数
        order.setActualMu(loggedArea.add(areaMu).setScale(2, RoundingMode.HALF_UP));
        order.setUpdatedAt(LocalDateTime.now());
        jobOrderRepository.save(order);

        // 回填无人机累计：亩数 + 飞行小时
        drone.setTotalAreaMu(nvl(drone.getTotalAreaMu()).add(areaMu).setScale(2, RoundingMode.HALF_UP));
        drone.setTotalFlightHours(nvl(drone.getTotalFlightHours())
                .add(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP));
        drone.setUpdatedAt(LocalDateTime.now());
        droneRepository.save(drone);

        // 回填飞手累计：架次 + 亩数
        Pilot pilot = pilotRepository.findById(order.getPilotId())
                .orElseThrow(() -> new BizException("作业单绑定的飞手不存在"));
        pilot.setTotalSorts((pilot.getTotalSorts() == null ? 0 : pilot.getTotalSorts()) + 1);
        pilot.setTotalAreaMu(nvl(pilot.getTotalAreaMu()).add(areaMu).setScale(2, RoundingMode.HALF_UP));
        pilot.setUpdatedAt(LocalDateTime.now());
        pilotRepository.save(pilot);

        // 异常架次：登记即自动立案进入待处置（同一事务内，立案失败则整笔架次登记回滚）
        if (abnormal) {
            handlingService.register(saved, order, pilot);
        }

        return toView(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(jobOrderId, FlightLogRow.class)
                .stream().filter(row -> saved.getId().equals(row.getId())).findFirst()
                .orElseThrow(() -> new BizException("架次登记失败，请刷新重试")), nameBook());
    }

    // ---------------- 内部工具 ----------------

    private Views.FlightLogView toView(FlightLogRow row, NameBook book) {
        Long droneId = book.droneOfOrder.get(row.getJobOrderId());
        AbnormalHandling handling = Boolean.TRUE.equals(row.getAbnormal())
                ? handlingRepository.findByFlightLogId(row.getId()).orElse(null)
                : null;
        return new Views.FlightLogView(row.getId(), row.getJobOrderId(), book.orderNo.get(row.getJobOrderId()),
                book.plotNo.get(book.plotOfOrder.get(row.getJobOrderId())), droneId,
                book.bodyNo.get(droneId),
                book.pilotName.get(book.pilotOfOrder.get(row.getJobOrderId())), row.getSeqNo(), row.getTakeoffAt(),
                row.getLandingAt(), row.getDurationMin(), row.getAreaMu(), row.getChemicalL(), row.getWindSpeed(),
                row.getTemperature(), Boolean.TRUE.equals(row.getAbnormal()), row.getAbnormalDesc(),
                handling == null ? null : handling.getId(),
                handling == null ? null : handling.getStatus(),
                handling == null ? null : PlantRules.handlingStatusName(handling.getStatus()));
    }

    /** 日志要显示「哪个单子 / 哪块地 / 哪台机 / 谁飞的」——一次性把四张表的编号查成 map */
    private NameBook nameBook() {
        Map<Long, String> orderNo = new HashMap<>();
        Map<Long, Long> plotOfOrder = new HashMap<>();
        Map<Long, Long> droneOfOrder = new HashMap<>();
        Map<Long, Long> pilotOfOrder = new HashMap<>();
        for (JobOrderBrief order : jobOrderRepository.findAllByOrderByJobDateDesc(JobOrderBrief.class)) {
            orderNo.put(order.getId(), order.getOrderNo());
            plotOfOrder.put(order.getId(), order.getPlotId());
            droneOfOrder.put(order.getId(), order.getDroneId());
            pilotOfOrder.put(order.getId(), order.getPilotId());
        }
        Map<Long, String> plotNo = new HashMap<>();
        for (PlotOption row : plotRepository.findAllByOrderByPlotNoAsc(PlotOption.class)) {
            plotNo.put(row.getId(), row.getPlotNo());
        }
        Map<Long, String> bodyNo = new HashMap<>();
        for (DroneOption row : droneRepository.findAllByOrderByBodyNoAsc(DroneOption.class)) {
            bodyNo.put(row.getId(), row.getBodyNo());
        }
        Map<Long, String> pilotName = new HashMap<>();
        for (PilotOption row : pilotRepository.findAllByOrderByPilotNoAsc(PilotOption.class)) {
            pilotName.put(row.getId(), row.getName());
        }
        return new NameBook(orderNo, plotOfOrder, droneOfOrder, pilotOfOrder, plotNo, bodyNo, pilotName);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record NameBook(Map<Long, String> orderNo, Map<Long, Long> plotOfOrder, Map<Long, Long> droneOfOrder,
                            Map<Long, Long> pilotOfOrder, Map<Long, String> plotNo, Map<Long, String> bodyNo,
                            Map<Long, String> pilotName) {
    }
}
