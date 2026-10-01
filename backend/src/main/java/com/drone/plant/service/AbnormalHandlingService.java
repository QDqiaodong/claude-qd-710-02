package com.drone.plant.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drone.plant.common.BizException;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.AbnormalAction;
import com.drone.plant.entity.AbnormalHandling;
import com.drone.plant.entity.Drone;
import com.drone.plant.entity.FlightLog;
import com.drone.plant.entity.JobOrder;
import com.drone.plant.entity.Pilot;
import com.drone.plant.projection.DroneOption;
import com.drone.plant.projection.FlightLogRow;
import com.drone.plant.projection.JobOrderBrief;
import com.drone.plant.projection.JobOrderRow;
import com.drone.plant.projection.PilotOption;
import com.drone.plant.projection.PlotOption;
import com.drone.plant.repository.AbnormalActionRepository;
import com.drone.plant.repository.AbnormalHandlingRepository;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.FlightLogRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;
import com.drone.plant.support.PlantRules;

/**
 * 模块五-延：异常架次停飞处置。
 *
 * <p>主线：异常架次登记即自动立案（PENDING 待处置）→ 值班主管选择
 * 继续观察 OBSERVING / 停飞机身 GROUNDED / 解除 CLOSED。
 *
 * <p>硬规则（与页面提示一一对应）：
 * <ol>
 *   <li>停飞：关联无人机立即转 MAINT，已有待飞作业单列入「必须改派」清单；
 *       作业中的机身不能停飞（先让作业单走完或取消）；</li>
 *   <li>解除：必须填写处置结论；解除后若该机身没有其他未结束处置，机身才回到 IDLE，
 *       否则保持 MAINT；</li>
 *   <li>机身仍有未结束处置（待处置 / 观察 / 停飞中）时，无人机台账不能手工切回在库；</li>
 *   <li>全部动作写 {@link AbnormalAction} 留痕，状态、处理人、结论以库里记录为准（刷新一致）。</li>
 * </ol>
 */
@Service
public class AbnormalHandlingService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final AbnormalHandlingRepository handlingRepository;
    private final AbnormalActionRepository actionRepository;
    private final FlightLogRepository flightLogRepository;
    private final JobOrderRepository jobOrderRepository;
    private final DroneRepository droneRepository;
    private final PilotRepository pilotRepository;
    private final PlotRepository plotRepository;

    public AbnormalHandlingService(AbnormalHandlingRepository handlingRepository,
                                   AbnormalActionRepository actionRepository,
                                   FlightLogRepository flightLogRepository, JobOrderRepository jobOrderRepository,
                                   DroneRepository droneRepository, PilotRepository pilotRepository,
                                   PlotRepository plotRepository) {
        this.handlingRepository = handlingRepository;
        this.actionRepository = actionRepository;
        this.flightLogRepository = flightLogRepository;
        this.jobOrderRepository = jobOrderRepository;
        this.droneRepository = droneRepository;
        this.pilotRepository = pilotRepository;
        this.plotRepository = plotRepository;
    }

    // ---------------- 查询 ----------------

    /** 按状态查处置单（状态为空查全部），可再按机身筛。 */
    @Transactional(readOnly = true)
    public List<Views.HandlingView> list(String status, Long droneId) {
        List<AbnormalHandling> rows;
        if (droneId != null) {
            List<String> statuses = (isBlank(status) || !PlantRules.isValidHandlingStatus(status))
                    ? PlantRules.HANDLING_STATUS_ORDER : List.of(status);
            rows = handlingRepository.findByDroneIdAndStatusInOrderByRegisteredAtDesc(droneId, statuses,
                    AbnormalHandling.class);
        } else if (!isBlank(status) && PlantRules.isValidHandlingStatus(status)) {
            rows = handlingRepository.findByStatusOrderByRegisteredAtDesc(status, AbnormalHandling.class);
        } else {
            rows = handlingRepository.findAllByOrderByRegisteredAtDesc(AbnormalHandling.class);
        }
        NameBook book = nameBook();
        List<Views.HandlingView> list = new ArrayList<>();
        for (AbnormalHandling row : rows) {
            list.add(toView(row, book, false));
        }
        return list;
    }

    /** 详情：架次快照 + 待飞改派清单 + 机身其他未结束处置 + 留痕时间线。 */
    @Transactional(readOnly = true)
    public Views.HandlingView detail(Long id) {
        return toView(requireHandling(id), nameBook(), true);
    }

    /** 值班主管下拉：在队且三级（高级）/ 教员。 */
    @Transactional(readOnly = true)
    public List<Views.SupervisorOptionView> supervisorOptions() {
        List<Views.SupervisorOptionView> list = new ArrayList<>();
        for (PilotOption row : pilotRepository.findByStatusOrderByPilotNoAsc("ACTIVE", PilotOption.class)) {
            if (PlantRules.reviewerLicenseAllowed(row.getLicenseLevel())) {
                list.add(new Views.SupervisorOptionView(row.getId(), row.getPilotNo(), row.getName(),
                        PlantRules.licenseName(row.getLicenseLevel())));
            }
        }
        return list;
    }

    // ---------------- 立案与处置动作 ----------------

    /**
     * 异常架次登记后自动立案：一条异常架次最多一条处置（唯一索引兜底）。
     * 返回新建处置单；若该架次已有处置（极端并发 / 补登记），返回原有那条，不重复立案。
     */
    @Transactional
    public AbnormalHandling register(FlightLog log, JobOrder order, Pilot registrant) {
        return handlingRepository.findByFlightLogId(log.getId()).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            AbnormalHandling handling = new AbnormalHandling();
            handling.setHandlingNo(nextNo(now));
            handling.setFlightLogId(log.getId());
            handling.setJobOrderId(order.getId());
            handling.setDroneId(order.getDroneId());
            handling.setStatus("PENDING");
            if (registrant != null) {
                handling.setRegisterPilotId(registrant.getId());
                handling.setRegisterName(registrant.getName());
            }
            handling.setRegisteredAt(log.getCreatedAt() == null ? now : log.getCreatedAt());
            handling.setUpdatedAt(now);
            AbnormalHandling saved = handlingRepository.save(handling);
            String desc = log.getAbnormalDesc();
            recordAction(saved, "REGISTER", registrant,
                    cap("异常架次登记后自动立案待处置" + (desc == null || desc.isBlank() ? "" : "：" + desc)), now);
            return saved;
        });
    }

    /** 待处置 / 停飞中 → 继续观察。 */
    @Transactional
    public Views.HandlingView observe(Long id, Long supervisorId, String comment) {
        return act(id, "OBSERVING", supervisorId, comment);
    }

    /** 待处置 / 观察中 → 停飞机身：机身立即转维修。 */
    @Transactional
    public Views.HandlingView ground(Long id, Long supervisorId, String comment) {
        return act(id, "GROUNDED", supervisorId, comment);
    }

    /** 待处置 / 观察中 / 停飞中 → 解除：必须填写处置结论。 */
    @Transactional
    public Views.HandlingView release(Long id, Long supervisorId, String conclusion) {
        String text = trim(conclusion);
        if (text.isEmpty()) {
            throw new BizException("解除前必须填写处置结论");
        }
        return act(id, "CLOSED", supervisorId, text);
    }

    private Views.HandlingView act(Long id, String target, Long supervisorId, String comment) {
        AbnormalHandling handling = requireHandling(id);
        if (!PlantRules.handlingFlowAllowed(handling.getStatus(), target)) {
            throw new BizException("处置单 " + handling.getHandlingNo() + " 当前为"
                    + PlantRules.handlingStatusName(handling.getStatus()) + "，不能执行"
                    + PlantRules.handlingStatusName(target));
        }
        Pilot supervisor = requireSupervisor(supervisorId);
        LocalDateTime now = LocalDateTime.now();
        String text = cap(trim(comment));

        if ("OBSERVING".equals(target)) {
            if (text.isEmpty()) {
                throw new BizException("选择继续观察时请填写观察意见");
            }
            handling.setStatus("OBSERVING");
            handling.setObserveComment(text);
            handling.setObservedAt(now);
        } else if ("GROUNDED".equals(target)) {
            Drone drone = droneRepository.findById(handling.getDroneId())
                    .orElseThrow(() -> new BizException("关联无人机不存在：id=" + handling.getDroneId()));
            if ("WORKING".equals(drone.getStatus())) {
                throw new BizException("机身 " + drone.getBodyNo()
                        + " 还有作业中的作业单，请先完成或取消该作业单后再停飞");
            }
            if (!"MAINT".equals(drone.getStatus())) {
                drone.setStatus("MAINT");
                drone.setUpdatedAt(now);
                droneRepository.save(drone);
            }
            List<JobOrder> pending = pendingOrders(drone.getId());
            if (!pending.isEmpty()) {
                StringBuilder sb = new StringBuilder(text);
                sb.append(text.isEmpty() ? "" : "；");
                sb.append("已有 ").append(pending.size()).append(" 张待飞作业单不得起飞，须改派：");
                for (int i = 0; i < pending.size(); i++) {
                    if (i > 0) {
                        sb.append("、");
                    }
                    sb.append(pending.get(i).getOrderNo());
                }
                text = cap(sb.toString());
            }
            handling.setStatus("GROUNDED");
            handling.setGroundComment(text);
            handling.setGroundedAt(now);
        } else {
            // CLOSED：结论非空已在 release() 校验
            handling.setStatus("CLOSED");
            handling.setConclusion(text);
            handling.setClosedAt(now);
            // 库里本单此刻仍是未结束状态：未结束计数减去本单自己，剩下的就是其他未结束处置。
            // 全部闭环后机身才允许回到在库，否则保持维修。
            long openCount = handlingRepository.countByDroneIdAndStatusIn(handling.getDroneId(),
                    PlantRules.HANDLING_OPEN_STATUSES);
            boolean otherOpen = openCount > 1;
            Drone drone = droneRepository.findById(handling.getDroneId()).orElse(null);
            if (drone != null && "MAINT".equals(drone.getStatus()) && !otherOpen
                    && !hasWorkingOrder(drone.getId())) {
                // 无其他未结束处置且没有在飞的单子才回到在库；作业仍在进行则维持作业中
                drone.setStatus("IDLE");
                drone.setUpdatedAt(now);
                droneRepository.save(drone);
            }
        }

        handling.setSupervisorId(supervisor.getId());
        handling.setSupervisorName(supervisor.getName());
        handling.setUpdatedAt(now);
        AbnormalHandling saved = handlingRepository.save(handling);
        String action = switch (target) {
            case "OBSERVING" -> "OBSERVE";
            case "GROUNDED" -> "GROUND";
            default -> "RELEASE";
        };
        recordAction(saved, action, supervisor, text, now);
        return detail(saved.getId());
    }

    // ---------------- 给其他 service 用的口径 ----------------

    /** 机身是否存在未结束处置（待处置 / 继续观察 / 停飞中）。 */
    @Transactional(readOnly = true)
    public boolean hasOpenHandling(Long droneId) {
        return droneId != null
                && handlingRepository.existsByDroneIdAndStatusIn(droneId, PlantRules.HANDLING_OPEN_STATUSES);
    }

    // ---------------- 内部工具 ----------------

    private AbnormalHandling requireHandling(Long id) {
        return handlingRepository.findById(id)
                .orElseThrow(() -> new BizException("处置单不存在：id=" + id));
    }

    private Pilot requireSupervisor(Long supervisorId) {
        if (supervisorId == null) {
            throw new BizException("请先选择当前值班主管");
        }
        Pilot pilot = pilotRepository.findById(supervisorId)
                .orElseThrow(() -> new BizException("值班主管不存在：id=" + supervisorId));
        if (!"ACTIVE".equals(pilot.getStatus())) {
            throw new BizException(pilot.getName() + " 当前不在队，不能担任值班主管");
        }
        if (!PlantRules.reviewerLicenseAllowed(pilot.getLicenseLevel())) {
            throw new BizException("值班主管须由高级（三级）或教员飞手担任，" + pilot.getName() + " 是"
                    + PlantRules.licenseName(pilot.getLicenseLevel()) + "飞手");
        }
        return pilot;
    }

    private List<JobOrder> pendingOrders(Long droneId) {
        List<JobOrder> list = new ArrayList<>();
        for (JobOrder order : jobOrderRepository.findAll()) {
            if (droneId.equals(order.getDroneId()) && "PENDING".equals(order.getStatus())) {
                list.add(order);
            }
        }
        return list;
    }

    private boolean hasWorkingOrder(Long droneId) {
        for (JobOrderBrief order : jobOrderRepository.findByDroneIdOrderByJobDateDesc(droneId,
                JobOrderBrief.class)) {
            if ("WORKING".equals(order.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private void recordAction(AbnormalHandling handling, String action, Pilot actor, String comment,
                              LocalDateTime at) {
        AbnormalAction row = new AbnormalAction();
        row.setHandlingId(handling.getId());
        row.setAction(action);
        if (actor != null) {
            row.setActorPilotId(actor.getId());
            row.setActorName(actor.getName());
        }
        row.setComment(comment);
        row.setCreatedAt(at);
        actionRepository.save(row);
    }

    /** 处置单号 AH-yyMMdd-序号：按当天立案数顺延。 */
    private String nextNo(LocalDateTime now) {
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        long seq = handlingRepository.countByRegisteredAtAfter(dayStart) + 1;
        return "AH-" + now.format(NO_DATE) + "-" + String.format("%02d", seq);
    }

    private boolean isOpen(String status) {
        return PlantRules.HANDLING_OPEN_STATUSES.contains(status);
    }

    private Views.HandlingView toView(AbnormalHandling h, NameBook book, boolean withDetail) {
        FlightLogRow log = book.flightLogs.get(h.getFlightLogId());
        JobOrderRow order = book.orders.get(h.getJobOrderId());
        DroneOption drone = book.drones.get(h.getDroneId());

        List<Views.HandlingPendingOrder> pendingOrders = List.of();
        long otherOpenCount = 0;
        List<Views.HandlingActionView> actions = List.of();
        if (withDetail) {
            pendingOrders = new ArrayList<>();
            for (JobOrderBrief brief : jobOrderRepository.findByDroneIdOrderByJobDateDesc(h.getDroneId(),
                    JobOrderBrief.class)) {
                if ("PENDING".equals(brief.getStatus())) {
                    pendingOrders.add(new Views.HandlingPendingOrder(brief.getId(), brief.getOrderNo(),
                            book.plotNo.get(brief.getPlotId()), brief.getJobDate()));
                }
            }
            otherOpenCount = handlingRepository.countByDroneIdAndStatusIn(h.getDroneId(),
                    PlantRules.HANDLING_OPEN_STATUSES) - (isOpen(h.getStatus()) ? 1 : 0);
            actions = new ArrayList<>();
            for (AbnormalAction a : actionRepository.findByHandlingIdOrderByIdAsc(h.getId())) {
                actions.add(new Views.HandlingActionView(a.getId(), a.getAction(),
                        PlantRules.handlingActionName(a.getAction()), a.getActorPilotId(), a.getActorName(),
                        a.getComment(), a.getCreatedAt()));
            }
        }
        return new Views.HandlingView(
                h.getId(), h.getHandlingNo(), h.getStatus(), PlantRules.handlingStatusName(h.getStatus()),
                h.getFlightLogId(), h.getJobOrderId(),
                order == null ? null : order.getOrderNo(),
                order == null ? null : book.plotNo.get(order.getPlotId()),
                order == null ? null : book.pilotName.get(order.getPilotId()),
                log == null ? null : log.getSeqNo(),
                log == null ? null : log.getTakeoffAt(),
                log == null ? null : log.getAbnormalDesc(),
                h.getDroneId(),
                drone == null ? null : drone.getBodyNo(),
                drone == null ? null : drone.getModel(),
                drone == null ? null : drone.getStatus(),
                drone == null ? null : PlantRules.droneStatusName(drone.getStatus()),
                h.getRegisterPilotId(), h.getRegisterName(), h.getSupervisorId(), h.getSupervisorName(),
                h.getObserveComment(), h.getGroundComment(), h.getConclusion(),
                h.getRegisteredAt(), h.getObservedAt(), h.getGroundedAt(), h.getClosedAt(), h.getUpdatedAt(),
                pendingOrders, otherOpenCount, actions);
    }

    /** 处置台要显示「哪张单 / 哪台机 / 哪一异常架次」——一次性查成 map。 */
    private NameBook nameBook() {
        Map<Long, FlightLogRow> flightLogs = new HashMap<>();
        for (FlightLogRow row : flightLogRepository.findAllByOrderByTakeoffAtDesc(FlightLogRow.class)) {
            flightLogs.put(row.getId(), row);
        }
        Map<Long, JobOrderRow> orders = new HashMap<>();
        for (JobOrderRow row : jobOrderRepository.findAllByOrderByJobDateDesc(JobOrderRow.class)) {
            orders.put(row.getId(), row);
        }
        Map<Long, DroneOption> drones = new HashMap<>();
        for (DroneOption row : droneRepository.findAllByOrderByBodyNoAsc(DroneOption.class)) {
            drones.put(row.getId(), row);
        }
        Map<Long, String> pilotName = new HashMap<>();
        for (PilotOption row : pilotRepository.findAllByOrderByPilotNoAsc(PilotOption.class)) {
            pilotName.put(row.getId(), row.getName());
        }
        Map<Long, String> plotNo = new HashMap<>();
        for (PlotOption row : plotRepository.findAllByOrderByPlotNoAsc(PlotOption.class)) {
            plotNo.put(row.getId(), row.getPlotNo());
        }
        return new NameBook(flightLogs, orders, drones, pilotName, plotNo);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** 意见列最长 255：追加待飞单号后也不能超，否则 MySQL 严格模式会整条拒绝 */
    private String cap(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record NameBook(Map<Long, FlightLogRow> flightLogs, Map<Long, JobOrderRow> orders,
                            Map<Long, DroneOption> drones, Map<Long, String> pilotName, Map<Long, String> plotNo) {
    }
}
