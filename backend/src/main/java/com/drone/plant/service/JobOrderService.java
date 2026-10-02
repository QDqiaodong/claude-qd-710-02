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
import com.drone.plant.dto.DroneDtos;
import com.drone.plant.dto.JobOrderDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.Drone;
import com.drone.plant.entity.JobOrder;
import com.drone.plant.entity.Pilot;
import com.drone.plant.entity.Plot;
import com.drone.plant.entity.MixingBatch;
import com.drone.plant.projection.DroneOption;
import com.drone.plant.projection.FlightLogStat;
import com.drone.plant.projection.JobOrderOption;
import com.drone.plant.projection.JobOrderRow;
import com.drone.plant.projection.PilotOption;
import com.drone.plant.projection.PlotOption;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.FlightLogRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.MixingBatchRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;
import com.drone.plant.support.PlantRules;

/**
 * 模块四：作业单。
 *
 * <p>状态机：待飞 PENDING → 作业中 WORKING → 已完成 DONE，待飞 / 作业中均可取消 CANCELED；
 * DONE 与 CANCELED 是终态。流转合法性由 {@link PlantRules#orderFlowAllowed} 判。
 * 列表查询走 {@link JobOrderRepository#findByStatusOrderByJobDateDesc(String, Class)} 这一族动态投影方法。
 */
@Service
public class JobOrderService {

    private final JobOrderRepository jobOrderRepository;
    private final PlotRepository plotRepository;
    private final DroneRepository droneRepository;
    private final PilotRepository pilotRepository;
    private final FlightLogRepository flightLogRepository;
    private final MixingBatchRepository mixingBatchRepository;
    private final MixingService mixingService;
    private final AbnormalHandlingService handlingService;

    public JobOrderService(JobOrderRepository jobOrderRepository, PlotRepository plotRepository,
                           DroneRepository droneRepository, PilotRepository pilotRepository,
                           FlightLogRepository flightLogRepository,
                           MixingBatchRepository mixingBatchRepository, MixingService mixingService,
                           AbnormalHandlingService handlingService) {
        this.jobOrderRepository = jobOrderRepository;
        this.plotRepository = plotRepository;
        this.droneRepository = droneRepository;
        this.pilotRepository = pilotRepository;
        this.flightLogRepository = flightLogRepository;
        this.mixingBatchRepository = mixingBatchRepository;
        this.mixingService = mixingService;
        this.handlingService = handlingService;
    }

    /**
     * 列表：四条动态投影查询按筛选维度各自命中——
     * 日期区间 / 地块 / 状态 / 机身 / 全量，返回形状统一是 {@link JobOrderRow}。
     */
    @Transactional(readOnly = true)
    public List<Views.JobOrderView> list(String status, Long plotId, Long droneId, LocalDate from, LocalDate to) {
        List<JobOrderRow> rows;
        if (droneId != null) {
            rows = new ArrayList<>();
            for (JobOrderRow row : jobOrderRepository.findByDroneIdOrderByJobDateDesc(droneId,
                    JobOrderRow.class)) {
                if (isBlank(status) || status.equals(row.getStatus())) {
                    rows.add(row);
                }
            }
        } else if (from != null && to != null) {
            rows = jobOrderRepository.findByJobDateBetweenOrderByJobDateDesc(from, to, JobOrderRow.class);
        } else if (plotId != null) {
            rows = jobOrderRepository.findByPlotIdOrderByJobDateDesc(plotId, JobOrderRow.class);
        } else if (!isBlank(status)) {
            rows = jobOrderRepository.findByStatusOrderByJobDateDesc(status, JobOrderRow.class);
        } else {
            rows = jobOrderRepository.findAllByOrderByJobDateDesc(JobOrderRow.class);
        }
        NameBook book = nameBook();
        List<Views.JobOrderView> list = new ArrayList<>();
        for (JobOrderRow row : rows) {
            list.add(toView(row, book));
        }
        return list;
    }

    /** 下拉框：同一个仓储方法换成 {@link JobOrderOption} 形状 */
    @Transactional(readOnly = true)
    public List<Views.JobOrderOptionView> options() {
        Map<Long, String> plotNoMap = plotNoMap();
        List<Views.JobOrderOptionView> list = new ArrayList<>();
        for (String status : List.of("WORKING", "PENDING", "DONE")) {
            for (JobOrderOption row : jobOrderRepository.findByStatusOrderByJobDateDesc(status,
                    JobOrderOption.class)) {
                list.add(new Views.JobOrderOptionView(row.getId(), row.getOrderNo(),
                        plotNoMap.get(row.getPlotId()), row.getJobDate(), row.getStatus(),
                        PlantRules.orderStatusName(row.getStatus())));
            }
        }
        return list;
    }

    @Transactional
    public Views.JobOrderView create(JobOrderDtos.SaveReq req) {
        String orderNo = trim(req.getOrderNo());
        Long plotId = req.getPlotId();
        Long droneId = req.getDroneId();
        Long pilotId = req.getPilotId();
        String formula = trim(req.getFormula());
        BigDecimal dosePerMu = nvl(req.getDosePerMu());
        BigDecimal plannedMu = nvl(req.getPlannedMu());
        LocalDate jobDate = req.getJobDate();
        return save(orderNo, plotId, droneId, pilotId, formula, dosePerMu, plannedMu, jobDate, req.getWeather());
    }

    /** 派机：无人机卡片墙上的「派机」按钮，把这台机直接绑到一张新的待飞作业单上 */
    @Transactional
    public Views.JobOrderView dispatch(Long droneId, DroneDtos.DispatchReq req) {
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new BizException("无人机不存在：id=" + droneId));
        return save(trim(req.getOrderNo()), req.getPlotId(), drone.getId(), req.getPilotId(),
                trim(req.getFormula()), nvl(req.getDosePerMu()), nvl(req.getPlannedMu()), req.getJobDate(),
                req.getWeather());
    }

    /** 待飞 → 作业中：地块进入作业中、无人机出库 */
    @Transactional
    public Views.JobOrderView start(Long id) {
        JobOrder order = requireOrder(id);
        requireFlow(order, "WORKING");
        Plot plot = requirePlot(order.getPlotId());
        Drone drone = requireDrone(order.getDroneId());
        Pilot pilot = requirePilot(order.getPilotId());
        if ("HOLD".equals(plot.getStatus())) {
            throw new BizException("地块 " + plot.getPlotNo() + " 已暂缓，先解除暂缓再起飞");
        }
        if (!"ACTIVE".equals(pilot.getStatus())) {
            throw new BizException("飞手 " + pilot.getName() + " 当前不在队，无法执行本次作业");
        }
        if (!"IDLE".equals(drone.getStatus())) {
            throw new BizException("机身 " + drone.getBodyNo() + " 当前状态为"
                    + PlantRules.droneStatusName(drone.getStatus()) + "，不能出库作业");
        }
        if (handlingService.hasOpenHandling(drone.getId())) {
            throw new BizException("机身 " + drone.getBodyNo()
                    + " 有未结束的异常处置（待处置 / 继续观察 / 停飞中），不能起飞，请先完成处置闭环或改派其他机身");
        }
        // 配药复核闸门：必须存在且仅存在一个仍有效的已锁定批次，快照与当前作业单、载荷逐项一致
        mixingService.assertTakeoffReady(order);
        order.setStatus("WORKING");
        order.setUpdatedAt(LocalDateTime.now());
        JobOrder saved = jobOrderRepository.save(order);
        plot.setStatus("WORKING");
        plot.setUpdatedAt(LocalDateTime.now());
        plotRepository.save(plot);
        drone.setStatus("WORKING");
        drone.setUpdatedAt(LocalDateTime.now());
        droneRepository.save(drone);
        return reload(saved.getId());
    }

    /**
     * 作业中 → 已完成：实际亩数只认该作业单已落库飞行日志的累计亩数。
     * 汇总为零拒绝完成；请求若携带实际亩数且与汇总不一致同样拒绝。
     * 状态机保证重复提交不再流转，统计不会被二次改变。
     */
    @Transactional
    public Views.JobOrderView done(Long id, JobOrderDtos.DoneReq req) {
        JobOrder order = requireOrder(id);
        requireFlow(order, "DONE");
        BigDecimal logged = loggedArea(order.getId()).setScale(2, RoundingMode.HALF_UP);
        if (logged.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("作业单 " + order.getOrderNo()
                    + " 还没有登记有效的飞行日志，日志累计亩数为 0，不能完成；请先登记架次再收口");
        }
        if (req != null && req.getActualMu() != null) {
            BigDecimal submitted = nvl(req.getActualMu()).setScale(2, RoundingMode.HALF_UP);
            if (submitted.compareTo(logged) != 0) {
                throw new BizException("实际作业亩数只能取该作业单已落库飞行日志的累计亩数，不能手工填写："
                        + "请求提交 " + submitted + " 亩，飞行日志汇总 " + logged
                        + " 亩，两者不一致，请以日志汇总 " + logged + " 亩为准");
            }
        }
        BigDecimal ceiling = nvl(order.getPlannedMu()).multiply(PlantRules.ACTUAL_TOLERANCE);
        if (logged.compareTo(ceiling) > 0) {
            throw new BizException("飞行日志累计亩数不能超过计划亩数的 1.2 倍（上限 "
                    + ceiling.setScale(2, RoundingMode.HALF_UP) + " 亩），当前日志汇总为 " + logged + " 亩");
        }
        order.setStatus("DONE");
        order.setActualMu(logged);
        order.setFinishedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        JobOrder saved = jobOrderRepository.save(order);

        Plot plot = requirePlot(order.getPlotId());
        plot.setStatus("DONE");
        plot.setUpdatedAt(LocalDateTime.now());
        plotRepository.save(plot);

        Drone drone = requireDrone(order.getDroneId());
        // 有未结束异常处置时不能直接回库：转维修等待处置闭环；否则正常回库
        if (handlingService.hasOpenHandling(drone.getId())) {
            drone.setStatus("MAINT");
        } else {
            drone.setStatus("IDLE");
        }
        drone.setUpdatedAt(LocalDateTime.now());
        droneRepository.save(drone);
        return reload(saved.getId());
    }

    /** 取消：作业中的单子要把地块和机身还原回待飞 / 在库 */
    @Transactional
    public Views.JobOrderView cancel(Long id, JobOrderDtos.CancelReq req) {
        JobOrder order = requireOrder(id);
        requireFlow(order, "CANCELED");
        order.setStatus("CANCELED");
        order.setUpdatedAt(LocalDateTime.now());
        if (req != null && req.getReason() != null && !req.getReason().isBlank()) {
            order.setWeather(order.getWeather() == null
                    ? "取消：" + req.getReason().trim()
                    : order.getWeather() + "（取消：" + req.getReason().trim() + "）");
        }
        JobOrder saved = jobOrderRepository.save(order);
        // 取消即让未失效配药批次立即失效，不能恢复、不能复制为有效结果
        mixingService.invalidateByJobOrder(order.getId(), "作业单 " + order.getOrderNo() + " 已取消");
        if (jobOrderRepository.countByPlotIdAndStatus(order.getPlotId(), "WORKING") == 0) {
            Plot plot = requirePlot(order.getPlotId());
            if ("WORKING".equals(plot.getStatus())) {
                plot.setStatus("PENDING");
                plot.setUpdatedAt(LocalDateTime.now());
                plotRepository.save(plot);
            }
        }
        Drone drone = requireDrone(order.getDroneId());
        if ("WORKING".equals(drone.getStatus())) {
            // 取消后无在飞单子：有未结束处置则转维修，否则回在库
            if (handlingService.hasOpenHandling(drone.getId())) {
                drone.setStatus("MAINT");
            } else {
                drone.setStatus("IDLE");
            }
            drone.setUpdatedAt(LocalDateTime.now());
            droneRepository.save(drone);
        }
        return reload(saved.getId());
    }

    /** 待飞作业单改派 / 改口径：触发失效的四项（无人机、配方、亩用量、计划亩数）一旦变化，原批次立即失效 */
    @Transactional
    public Views.JobOrderView update(Long id, JobOrderDtos.UpdateReq req) {
        JobOrder order = requireOrder(id);
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException("作业单 " + order.getOrderNo() + " 当前为"
                    + PlantRules.orderStatusName(order.getStatus()) + "，只有待飞作业单允许改派或修改口径");
        }
        Long droneId = req.getDroneId() == null ? order.getDroneId() : req.getDroneId();
        Long pilotId = req.getPilotId() == null ? order.getPilotId() : req.getPilotId();
        Long plotId = req.getPlotId() == null ? order.getPlotId() : req.getPlotId();
        String formula = req.getFormula() == null ? order.getFormula() : req.getFormula().trim();
        BigDecimal dosePerMu = req.getDosePerMu() == null ? order.getDosePerMu() : req.getDosePerMu();
        BigDecimal plannedMu = req.getPlannedMu() == null ? order.getPlannedMu() : req.getPlannedMu();

        Plot plot = requirePlot(plotId);
        Drone drone = requireDrone(droneId);
        Pilot pilot = requirePilot(pilotId);
        if ("HOLD".equals(plot.getStatus())) {
            throw new BizException("地块 " + plot.getPlotNo() + " 处于暂缓状态，不能派航");
        }
        if (!"IDLE".equals(drone.getStatus()) && !droneId.equals(order.getDroneId())) {
            throw new BizException("机身 " + drone.getBodyNo() + " 当前状态为"
                    + PlantRules.droneStatusName(drone.getStatus()) + "，只有「在库」的机才能改派");
        }
        if (!droneId.equals(order.getDroneId()) && handlingService.hasOpenHandling(droneId)) {
            throw new BizException("机身 " + drone.getBodyNo()
                    + " 有未结束的异常处置，不能改派给它，请选一台处置已闭环的在库机身");
        }
        if (!"ACTIVE".equals(pilot.getStatus())) {
            throw new BizException("飞手 " + pilot.getName() + " 状态为"
                    + PlantRules.pilotStatusName(pilot.getStatus()) + "，不能派工");
        }
        if (!PlantRules.levelsForTerrain(plot.getTerrain()).contains(pilot.getLicenseLevel())) {
            throw new BizException(PlantRules.terrainName(plot.getTerrain()) + "地块要求["
                    + PlantRules.licenseName(PlantRules.levelsForTerrain(plot.getTerrain()).get(0))
                    + "]及以上执照，" + pilot.getName() + " 是"
                    + PlantRules.licenseName(pilot.getLicenseLevel()) + "飞手");
        }
        if (formula.isEmpty()) {
            throw new BizException("药剂配方不能为空");
        }
        if (dosePerMu.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("亩用量必须大于 0");
        }
        if (dosePerMu.compareTo(PlantRules.MAX_DOSE_PER_MU) > 0) {
            throw new BizException("亩用量上限是 " + PlantRules.MAX_DOSE_PER_MU + " L/亩，收到的是 " + dosePerMu);
        }
        if (plannedMu.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("计划作业亩数必须大于 0");
        }
        BigDecimal plotCeiling = nvl(plot.getAreaMu()).multiply(PlantRules.PLANNED_TOLERANCE);
        if (plannedMu.compareTo(plotCeiling) > 0) {
            throw new BizException("地块 " + plot.getPlotNo() + " 面积 " + plot.getAreaMu()
                    + " 亩，计划亩数不能超过其 1.1 倍（上限 " + plotCeiling.setScale(2, RoundingMode.HALF_UP) + " 亩）");
        }

        // 先记下触发失效的具体项，改完单后据此让原已锁定批次立即失效
        List<String> caliberChanges = new ArrayList<>();
        if (!droneId.equals(order.getDroneId())) {
            Drone oldDrone = droneRepository.findById(order.getDroneId()).orElse(null);
            caliberChanges.add("改派无人机（" + (oldDrone == null ? order.getDroneId() : oldDrone.getBodyNo())
                    + " → " + drone.getBodyNo() + "）");
        }
        if (!formula.equals(order.getFormula())) {
            caliberChanges.add("药剂配方变化");
        }
        if (dosePerMu.setScale(2, RoundingMode.HALF_UP).compareTo(nvl(order.getDosePerMu())) != 0) {
            caliberChanges.add("亩用量变化（" + order.getDosePerMu() + " → "
                    + dosePerMu.setScale(2, RoundingMode.HALF_UP) + " L/亩）");
        }
        if (plannedMu.setScale(2, RoundingMode.HALF_UP).compareTo(nvl(order.getPlannedMu())) != 0) {
            caliberChanges.add("计划亩数变化（" + order.getPlannedMu() + " → "
                    + plannedMu.setScale(2, RoundingMode.HALF_UP) + " 亩）");
        }
        boolean caliberChanged = !caliberChanges.isEmpty();

        order.setPlotId(plotId);
        order.setDroneId(droneId);
        order.setPilotId(pilotId);
        order.setFormula(formula);
        order.setDosePerMu(dosePerMu.setScale(2, RoundingMode.HALF_UP));
        order.setPlannedMu(plannedMu.setScale(2, RoundingMode.HALF_UP));
        if (req.getJobDate() != null) {
            order.setJobDate(req.getJobDate());
        }
        if (req.getWeather() != null) {
            order.setWeather(req.getWeather().trim().isEmpty() ? null : req.getWeather().trim());
        }
        order.setUpdatedAt(LocalDateTime.now());
        JobOrder saved = jobOrderRepository.save(order);

        if (caliberChanged) {
            mixingService.invalidateByJobOrder(order.getId(),
                    "作业单 " + order.getOrderNo() + " " + String.join("、", caliberChanges)
                            + "，原配药依据失效，只能按新条件重新发起");
        }
        return reload(saved.getId());
    }

    // ---------------- 内部工具 ----------------

    private Views.JobOrderView save(String orderNo, Long plotId, Long droneId, Long pilotId, String formula,
                                    BigDecimal dosePerMu, BigDecimal plannedMu, LocalDate jobDate, String weather) {
        if (orderNo.isEmpty()) {
            throw new BizException("作业单号不能为空");
        }
        if (jobOrderRepository.existsByOrderNo(orderNo)) {
            throw new BizException("作业单号 " + orderNo + " 已存在");
        }
        if (plotId == null) {
            throw new BizException("请先选择作业地块");
        }
        if (droneId == null) {
            throw new BizException("请先选择作业无人机");
        }
        if (pilotId == null) {
            throw new BizException("请先指定飞手");
        }
        Plot plot = requirePlot(plotId);
        Drone drone = requireDrone(droneId);
        Pilot pilot = requirePilot(pilotId);
        if ("HOLD".equals(plot.getStatus())) {
            throw new BizException("地块 " + plot.getPlotNo() + " 处于暂缓状态，不能派航");
        }
        if (!"IDLE".equals(drone.getStatus())) {
            throw new BizException("机身 " + drone.getBodyNo() + " 当前状态为"
                    + PlantRules.droneStatusName(drone.getStatus()) + "，只有「在库」的机才能派航");
        }
        if (handlingService.hasOpenHandling(drone.getId())) {
            throw new BizException("机身 " + drone.getBodyNo()
                    + " 有未结束的异常处置（待处置 / 继续观察 / 停飞中），不能再派新作业单");
        }
        if (!"ACTIVE".equals(pilot.getStatus())) {
            throw new BizException("飞手 " + pilot.getName() + " 状态为"
                    + PlantRules.pilotStatusName(pilot.getStatus()) + "，不能派工");
        }
        if (!PlantRules.levelsForTerrain(plot.getTerrain()).contains(pilot.getLicenseLevel())) {
            throw new BizException(PlantRules.terrainName(plot.getTerrain()) + "地块要求["
                    + PlantRules.licenseName(PlantRules.levelsForTerrain(plot.getTerrain()).get(0))
                    + "]及以上执照，" + pilot.getName() + " 是"
                    + PlantRules.licenseName(pilot.getLicenseLevel()) + "飞手");
        }
        if (formula.isEmpty()) {
            throw new BizException("药剂配方不能为空");
        }
        if (dosePerMu.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("亩用量必须大于 0");
        }
        if (dosePerMu.compareTo(PlantRules.MAX_DOSE_PER_MU) > 0) {
            throw new BizException("亩用量上限是 " + PlantRules.MAX_DOSE_PER_MU + " L/亩，收到的是 " + dosePerMu);
        }
        if (plannedMu.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("计划作业亩数必须大于 0");
        }
        BigDecimal plotCeiling = nvl(plot.getAreaMu()).multiply(PlantRules.PLANNED_TOLERANCE);
        if (plannedMu.compareTo(plotCeiling) > 0) {
            throw new BizException("地块 " + plot.getPlotNo() + " 面积 " + plot.getAreaMu()
                    + " 亩，计划亩数不能超过其 1.1 倍（上限 " + plotCeiling.setScale(2, RoundingMode.HALF_UP) + " 亩）");
        }
        if (jobDate == null) {
            throw new BizException("作业日期不能为空");
        }
        JobOrder order = new JobOrder();
        order.setOrderNo(orderNo);
        order.setPlotId(plot.getId());
        order.setDroneId(drone.getId());
        order.setPilotId(pilot.getId());
        order.setFormula(formula);
        order.setDosePerMu(dosePerMu.setScale(2, RoundingMode.HALF_UP));
        order.setPlannedMu(plannedMu.setScale(2, RoundingMode.HALF_UP));
        order.setJobDate(jobDate);
        order.setWeather(trim(weather).isEmpty() ? null : trim(weather));
        order.setStatus("PENDING");
        order.setActualMu(BigDecimal.ZERO);
        order.setUpdatedAt(LocalDateTime.now());
        return reload(jobOrderRepository.save(order).getId());
    }

    private Views.JobOrderView reload(Long id) {
        for (JobOrderRow row : jobOrderRepository.findAllByOrderByJobDateDesc(JobOrderRow.class)) {
            if (id.equals(row.getId())) {
                return toView(row, nameBook());
            }
        }
        throw new BizException("作业单数据已失效，请刷新重试");
    }

    private BigDecimal loggedArea(Long jobOrderId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FlightLogStat log : flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(jobOrderId,
                FlightLogStat.class)) {
            sum = sum.add(nvl(log.getAreaMu()));
        }
        return sum;
    }

    private Views.JobOrderView toView(JobOrderRow row, NameBook book) {
        BigDecimal planned = nvl(row.getPlannedMu());
        BigDecimal actual = nvl(row.getActualMu());
        BigDecimal logged = book.loggedMu.getOrDefault(row.getId(), BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        int progress = planned.compareTo(BigDecimal.ZERO) <= 0
                ? 0
                : actual.multiply(BigDecimal.valueOf(100)).divide(planned, 0, RoundingMode.HALF_UP).intValue();
        BigDecimal chemical = book.chemical.getOrDefault(row.getId(), BigDecimal.ZERO);
        MixingBatch mixing = book.openBatch.get(row.getId());
        String droneStatus = book.droneStatus.get(row.getDroneId());
        return new Views.JobOrderView(row.getId(), row.getOrderNo(), row.getPlotId(),
                book.plotNo.get(row.getPlotId()), book.farmer.get(row.getPlotId()), row.getDroneId(),
                book.bodyNo.get(row.getDroneId()), book.model.get(row.getDroneId()),
                droneStatus, PlantRules.droneStatusName(droneStatus),
                book.openHandlingDrones.contains(row.getDroneId()), row.getPilotId(),
                book.pilotName.get(row.getPilotId()), row.getFormula(), row.getDosePerMu(), planned, actual, logged,
                row.getJobDate(), row.getWeather(), row.getStatus(),
                PlantRules.orderStatusName(row.getStatus()), Math.min(progress, 100),
                book.sortCount.getOrDefault(row.getId(), 0L).intValue(), chemical, row.getFinishedAt(),
                null,
                mixing == null ? null : mixing.getId(),
                mixing == null ? null : mixing.getStatus(),
                mixing == null ? null : PlantRules.mixingStatusName(mixing.getStatus()));
    }

    /** 名字与数量的查询用缓存体：避免每行走一次库 */
    private NameBook nameBook() {
        Map<Long, String> plotNo = plotNoMap();
        Map<Long, String> farmer = new HashMap<>();
        for (PlotOption row : plotRepository.findAllByOrderByPlotNoAsc(PlotOption.class)) {
            farmer.put(row.getId(), row.getFarmerName());
        }
        Map<Long, String> bodyNo = new HashMap<>();
        Map<Long, String> model = new HashMap<>();
        Map<Long, String> droneStatus = new HashMap<>();
        for (DroneOption row : droneRepository.findAllByOrderByBodyNoAsc(DroneOption.class)) {
            bodyNo.put(row.getId(), row.getBodyNo());
            model.put(row.getId(), row.getModel());
            droneStatus.put(row.getId(), row.getStatus());
        }
        Map<Long, String> pilotName = new HashMap<>();
        for (PilotOption row : pilotRepository.findAllByOrderByPilotNoAsc(PilotOption.class)) {
            pilotName.put(row.getId(), row.getName());
        }
        Map<Long, BigDecimal> chemical = new HashMap<>();
        Map<Long, BigDecimal> loggedMu = new HashMap<>();
        Map<Long, Long> sortCount = new HashMap<>();
        for (FlightLogStat log : flightLogRepository.findAllByOrderByTakeoffAtDesc(FlightLogStat.class)) {
            Long orderId = log.getJobOrderId();
            chemical.merge(orderId, nvl(log.getChemicalL()), BigDecimal::add);
            loggedMu.merge(orderId, nvl(log.getAreaMu()), BigDecimal::add);
            sortCount.merge(orderId, 1L, Long::sum);
        }
        // 每张作业单当前仍有效的配药批次（草稿/待复核/已驳回/已锁定），供列表“配药”列直达
        Map<Long, MixingBatch> openBatch = new HashMap<>();
        for (MixingBatch batch : mixingBatchRepository.findAll()) {
            if (!"EXPIRED".equals(batch.getStatus())) {
                openBatch.put(batch.getJobOrderId(), batch);
            }
        }
        // 仍有未结束异常处置的机身：待飞单明确提示改派
        java.util.Set<Long> openHandlingDrones = new java.util.HashSet<>();
        for (DroneOption row : droneRepository.findAllByOrderByBodyNoAsc(DroneOption.class)) {
            if (handlingService.hasOpenHandling(row.getId())) {
                openHandlingDrones.add(row.getId());
            }
        }
        return new NameBook(plotNo, farmer, bodyNo, model, droneStatus, pilotName, chemical, loggedMu, sortCount,
                openBatch, openHandlingDrones);
    }

    private Map<Long, String> plotNoMap() {
        Map<Long, String> map = new HashMap<>();
        for (PlotOption row : plotRepository.findAllByOrderByPlotNoAsc(PlotOption.class)) {
            map.put(row.getId(), row.getPlotNo());
        }
        return map;
    }

    private JobOrder requireOrder(Long id) {
        return jobOrderRepository.findById(id).orElseThrow(() -> new BizException("作业单不存在：id=" + id));
    }

    private Plot requirePlot(Long id) {
        return plotRepository.findById(id).orElseThrow(() -> new BizException("地块不存在：id=" + id));
    }

    private Drone requireDrone(Long id) {
        return droneRepository.findById(id).orElseThrow(() -> new BizException("无人机不存在：id=" + id));
    }

    private Pilot requirePilot(Long id) {
        return pilotRepository.findById(id).orElseThrow(() -> new BizException("飞手不存在：id=" + id));
    }

    private void requireFlow(JobOrder order, String target) {
        if (!PlantRules.orderFlowAllowed(order.getStatus(), target)) {
            throw new BizException("作业单 " + order.getOrderNo() + " 当前状态为"
                    + PlantRules.orderStatusName(order.getStatus()) + "，不能流转到"
                    + PlantRules.orderStatusName(target));
        }
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record NameBook(Map<Long, String> plotNo, Map<Long, String> farmer, Map<Long, String> bodyNo,
                            Map<Long, String> model, Map<Long, String> droneStatus,
                            Map<Long, String> pilotName, Map<Long, BigDecimal> chemical,
                            Map<Long, BigDecimal> loggedMu,
                            Map<Long, Long> sortCount, Map<Long, MixingBatch> openBatch,
                            java.util.Set<Long> openHandlingDrones) {
    }
}
