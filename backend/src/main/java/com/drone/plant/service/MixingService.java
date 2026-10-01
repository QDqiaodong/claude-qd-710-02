package com.drone.plant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drone.plant.common.BizException;
import com.drone.plant.dto.MixingDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.Drone;
import com.drone.plant.entity.JobOrder;
import com.drone.plant.entity.MixingBatch;
import com.drone.plant.entity.MixingBucket;
import com.drone.plant.entity.MixingReview;
import com.drone.plant.entity.Pilot;
import com.drone.plant.projection.PilotOption;
import com.drone.plant.projection.PlotOption;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.MixingBatchRepository;
import com.drone.plant.repository.MixingBucketRepository;
import com.drone.plant.repository.MixingReviewRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;
import com.drone.plant.support.MixingPlanner;
import com.drone.plant.support.PlantRules;

/**
 * 模块六：作业前配药复核台。
 *
 * <p>一条主线：待飞作业单 —发起→ 草稿（自动最少桶数分桶）—提交→ 待复核
 * —另一名三级/教员复核→ 已锁定（可起飞）；复核不通过 → 已驳回 → 改桶后再次提交。
 * 作业单取消 / 改派 / 计划口径变化，未失效批次立即 EXPIRED（终态，不可恢复、不能复制为有效结果）。
 *
 * <p>三道硬口径，任何一环都不让步：
 * <ol>
 *   <li><b>载荷安全</b>：每桶必须 &gt; 0 且 ≤ 快照载荷，自动方案桶数取最小值；</li>
 *   <li><b>总量守恒</b>：各桶合计与计划总药量（计划亩数×亩用量）按 0.01L 精确相等，
 *       自动方案把余量从第 1 桶起均摊，人工调整也逐桶复核，不靠最后一桶吞误差；</li>
 *   <li><b>复核留痕</b>：提交 / 驳回 / 锁定 / 失效各写一条 {@link MixingReview}。</li>
 * </ol>
 *
 * <p>双人同时确认：{@link MixingBatchRepository#findByIdForUpdate} 行锁串行化，
 * 后到者拿到锁后读到 LOCKED，收到明确“已被 XX 锁定”；唯一索引再兜底。
 */
@Service
public class MixingService {

    private final MixingBatchRepository batchRepository;
    private final MixingBucketRepository bucketRepository;
    private final MixingReviewRepository reviewRepository;
    private final JobOrderRepository jobOrderRepository;
    private final DroneRepository droneRepository;
    private final PilotRepository pilotRepository;
    private final PlotRepository plotRepository;

    public MixingService(MixingBatchRepository batchRepository, MixingBucketRepository bucketRepository,
                         MixingReviewRepository reviewRepository, JobOrderRepository jobOrderRepository,
                         DroneRepository droneRepository, PilotRepository pilotRepository,
                         PlotRepository plotRepository) {
        this.batchRepository = batchRepository;
        this.bucketRepository = bucketRepository;
        this.reviewRepository = reviewRepository;
        this.jobOrderRepository = jobOrderRepository;
        this.droneRepository = droneRepository;
        this.pilotRepository = pilotRepository;
        this.plotRepository = plotRepository;
    }

    // ---------------- 查询入口 ----------------

    /** 按状态查批次（状态为空查全部）。 */
    @Transactional(readOnly = true)
    public List<Views.MixingBatchView> list(String status) {
        List<MixingBatch> batches = isBlank(status)
                ? batchRepository.findAllByOrderByUpdatedAtDesc()
                : batchRepository.findByStatusOrderByUpdatedAtDesc(validStatus(status));
        NameBook book = nameBook();
        List<Views.MixingBatchView> list = new ArrayList<>();
        for (MixingBatch batch : batches) {
            list.add(toView(batch, book, false));
        }
        return list;
    }

    /** 从作业单直接进入对应批次：返回该单全部批次（最新在前），无则空列表。 */
    @Transactional(readOnly = true)
    public List<Views.MixingBatchView> listByJobOrder(Long jobOrderId) {
        NameBook book = nameBook();
        List<Views.MixingBatchView> list = new ArrayList<>();
        for (MixingBatch batch : batchRepository.findByJobOrderIdOrderByIdDesc(jobOrderId)) {
            list.add(toView(batch, book, false));
        }
        return list;
    }

    /** 详情：分桶明细 + 复核时间线 + 快照差异。 */
    @Transactional(readOnly = true)
    public Views.MixingBatchView detail(Long id) {
        return toView(requireBatch(id), nameBook(), true);
    }

    /** 合格复核人下拉：在队且三级 / 教员。 */
    @Transactional(readOnly = true)
    public List<Views.PilotOptionView> reviewerOptions() {
        List<Views.PilotOptionView> list = new ArrayList<>();
        for (PilotOption row : pilotRepository.findByStatusOrderByPilotNoAsc("ACTIVE", PilotOption.class)) {
            if (PlantRules.reviewerLicenseAllowed(row.getLicenseLevel())) {
                list.add(new Views.PilotOptionView(row.getId(), row.getPilotNo(), row.getName(),
                        PlantRules.licenseName(row.getLicenseLevel()), row.getStatus()));
            }
        }
        return list;
    }

    /** 飞手下拉（在队）：配药人选择用。 */
    @Transactional(readOnly = true)
    public List<Views.PilotOptionView> activePilotOptions() {
        List<Views.PilotOptionView> list = new ArrayList<>();
        for (PilotOption row : pilotRepository.findByStatusOrderByPilotNoAsc("ACTIVE", PilotOption.class)) {
            list.add(new Views.PilotOptionView(row.getId(), row.getPilotNo(), row.getName(),
                    PlantRules.licenseName(row.getLicenseLevel()), row.getStatus()));
        }
        return list;
    }

    // ---------------- 批次生命周期 ----------------

    /** 从待飞作业单发起配药批次：快照 + 自动（或人工）分桶，落草稿。 */
    @Transactional
    public Views.MixingBatchView create(MixingDtos.CreateReq req) {
        Long orderId = req.getJobOrderId();
        JobOrder order = requireOrder(orderId);
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException("作业单 " + order.getOrderNo() + " 当前为"
                    + PlantRules.orderStatusName(order.getStatus())
                    + "，只有待飞作业单才能发起配药；已经起飞的作业单不允许再新建批次");
        }
        Pilot preparer = requireActivePilot(req.getPreparedPilotId(), "配药人");

        if (batchRepository.countByJobOrderIdAndStatusNot(orderId, "EXPIRED") > 0) {
            MixingBatch existing = batchRepository
                    .findFirstByJobOrderIdAndStatusNotOrderByIdDesc(orderId, "EXPIRED")
                    .orElseThrow(() -> new BizException("该作业单已有未失效批次，请直接进入处理"));
            throw new BizException("作业单 " + order.getOrderNo() + " 已存在"
                    + PlantRules.mixingStatusName(existing.getStatus()) + "批次 " + existing.getBatchNo()
                    + "，请直接处理或等待其失效后再重新发起");
        }

        Drone drone = requireDrone(order.getDroneId());
        BigDecimal total = totalChemical(order);
        List<BigDecimal> amounts = req.getBuckets() == null || req.getBuckets().isEmpty()
                ? MixingPlanner.plan(total, drone.getMaxPayload())
                : normalizeAndValidate(req.getBuckets(), total, drone.getMaxPayload());

        LocalDateTime now = LocalDateTime.now();
        MixingBatch batch = new MixingBatch();
        batch.setBatchNo(nextBatchNo());
        batch.setJobOrderId(order.getId());
        batch.setStatus("DRAFT");
        batch.setPreparedPilotId(preparer.getId());
        snapshotFrom(batch, order, drone, total, amounts.size());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        MixingBatch saved;
        try {
            saved = batchRepository.save(batch);
            batchRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BizException("作业单 " + order.getOrderNo()
                    + " 已存在未失效配药批次（可能是并发发起），请刷新列表直接处理，不能重复发起");
        }
        saveBuckets(saved.getId(), amounts);
        logReview(saved.getId(), "CREATE", preparer, null, "发起配药批次，系统生成默认分桶", now);
        return detail(saved.getId());
    }

    /** 人工调整各桶药量（草稿 / 已驳回）；逐桶校验，不靠最后一桶吞误差。 */
    @Transactional
    public Views.MixingBatchView updateBuckets(Long id, MixingDtos.BucketsReq req) {
        MixingBatch batch = lockBatch(id);
        Pilot pilot = requireActivePilot(req.getPilotId(), "操作飞手");
        requireOrderPending(batch);
        if (!"DRAFT".equals(batch.getStatus()) && !"REJECTED".equals(batch.getStatus())) {
            throw new BizException("批次 " + batch.getBatchNo() + " 当前为"
                    + PlantRules.mixingStatusName(batch.getStatus()) + "，不能再调整分桶");
        }
        if (!batch.getPreparedPilotId().equals(pilot.getId())) {
            throw new BizException("只有配药人本人 " + pilotName(batch.getPreparedPilotId())
                    + " 才能调整本批次的分桶");
        }
        List<BigDecimal> amounts = normalizeAndValidate(req.getAmounts(),
                batch.getTotalL(), batch.getMaxPayloadSnapshot());
        bucketRepository.deleteByBatchId(batch.getId());
        bucketRepository.flush();
        saveBuckets(batch.getId(), amounts);
        batch.setBucketCount(amounts.size());
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        return detail(batch.getId());
    }

    /** 提交复核前重新校验分桶结果（载荷安全 + 总量守恒），通过后进入待复核。 */
    @Transactional
    public Views.MixingBatchView submit(Long id, MixingDtos.ActorReq req) {
        MixingBatch batch = lockBatch(id);
        Pilot pilot = requireActivePilot(req.getPilotId(), "配药人");
        requireOrderPending(batch);
        if (!"DRAFT".equals(batch.getStatus()) && !"REJECTED".equals(batch.getStatus())) {
            throw new BizException("只有草稿或已驳回的批次能提交复核，当前为"
                    + PlantRules.mixingStatusName(batch.getStatus()));
        }        if (!batch.getPreparedPilotId().equals(pilot.getId())) {
            throw new BizException("只有配药人本人 " + pilotName(batch.getPreparedPilotId()) + " 才能提交复核");
        }
        // 提交前服务端重新校验：防止绕过前端改库或留下旧口径的桶
        validateAgainst(batch, bucketsOf(batch.getId()));
        LocalDateTime now = LocalDateTime.now();
        batch.setStatus("PENDING_REVIEW");
        batch.setSubmittedAt(now);
        batch.setRejectReason(null);
        batch.setReviewerId(null);
        batch.setLockedAt(null);
        batch.setUpdatedAt(now);
        batchRepository.save(batch);
        logReview(batch.getId(), "SUBMIT", pilot, null, null, now);
        return detail(batch.getId());
    }

    /** 驳回必须填写原因，批次退回已驳回，配药人改桶后可再次提交。 */
    @Transactional
    public Views.MixingBatchView reject(Long id, MixingDtos.RejectReq req) {
        MixingBatch batch = lockBatch(id);
        Pilot reviewer = requireReviewer(req.getPilotId(), batch);
        requireOrderPending(batch);
        if (!"PENDING_REVIEW".equals(batch.getStatus())) {
            throw new BizException("只有待复核批次能驳回，当前为" + PlantRules.mixingStatusName(batch.getStatus()));
        }
        String reason = req.getReason() == null ? "" : req.getReason().trim();
        if (reason.isEmpty()) {
            throw new BizException("驳回必须填写原因，配药人需要据此修改");
        }
        LocalDateTime now = LocalDateTime.now();
        batch.setStatus("REJECTED");
        batch.setRejectReason(reason);
        batch.setReviewerId(reviewer.getId());
        batch.setUpdatedAt(now);
        batchRepository.save(batch);
        logReview(batch.getId(), "REJECT", reviewer, reason, null, now);
        return detail(batch.getId());
    }

    /**
     * 双人并发确认：行锁内条件更新。
     * 抢到的人锁定成功；另一名复核人刷新或重试时得到明确“已被 XX 锁定”，不会产生第二条锁定记录。
     */
    @Transactional
    public Views.MixingBatchView lock(Long id, MixingDtos.LockReq req) {
        MixingBatch batch = lockBatch(id);
        Pilot reviewer = requireReviewer(req.getPilotId(), batch);
        requireOrderPending(batch);
        if ("LOCKED".equals(batch.getStatus())) {
            throw new BizException("该批次已被处理：" + pilotName(batch.getReviewerId())
                    + " 已于 " + fmt(batch.getLockedAt()) + " 锁定，无需重复操作（状态已刷新）");
        }
        if (!"PENDING_REVIEW".equals(batch.getStatus())) {
            throw new BizException("批次当前为" + PlantRules.mixingStatusName(batch.getStatus())
                    + "，不是待复核，无法锁定");
        }
        JobOrder order = requireOrder(batch.getJobOrderId());
        Drone currentDrone = droneRepository.findById(order.getDroneId()).orElse(null);
        List<String> snapshotDiffs = snapshotDiffs(batch, order, currentDrone);
        if (!snapshotDiffs.isEmpty()) {
            throw new BizException("配药批次 " + batch.getBatchNo()
                    + " 的快照与当前作业口径不一致，不能锁定，具体不一致项："
                    + String.join("；", snapshotDiffs) + "。请按当前条件重新发起配药复核");
        }
        // 锁定前同样重新校验分桶，守住载荷安全与总量守恒
        validateAgainst(batch, bucketsOf(batch.getId()));
        LocalDateTime now = LocalDateTime.now();
        batch.setStatus("LOCKED");
        batch.setReviewerId(reviewer.getId());
        batch.setLockedAt(now);
        batch.setUpdatedAt(now);
        try {
            batchRepository.save(batch);
            batchRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BizException("该批次已被另一名复核人锁定（并发确认只允许一人成功），请刷新查看处理结果");
        }
        logReview(batch.getId(), "LOCK", reviewer, "复核通过，批次锁定", null, now);
        return detail(batch.getId());
    }

    // ---------------- 起飞校验与失效联动 ----------------

    /**
     * 作业单起飞前的配药闸门：存在且仅存在一个仍有效的已锁定批次，且快照与当前作业单、
     * 无人机载荷完全一致、分桶仍守恒不超载。任何不满足都抛出带具体不一致项的业务异常。
     */
    @Transactional(readOnly = true)
    public void assertTakeoffReady(JobOrder order) {
        long locked = batchRepository.countByJobOrderIdAndStatus(order.getId(), "LOCKED");
        if (locked == 0) {
            if (batchRepository.countByJobOrderIdAndStatusNot(order.getId(), "EXPIRED") == 0) {
                throw new BizException("作业单 " + order.getOrderNo()
                        + " 还没有配药批次：请先发起配药并由另一名三级/教员复核锁定后再起飞");
            }
            throw new BizException("作业单 " + order.getOrderNo()
                    + " 没有仍有效的已锁定配药批次：当前批次未通过复核或已失效，请按当前条件重新发起配药");
        }
        if (locked > 1) {
            throw new BizException("作业单 " + order.getOrderNo() + " 存在 " + locked
                    + " 个已锁定配药批次，必须只保留一个，请联系管理员核对");
        }
        MixingBatch batch = batchRepository
                .findFirstByJobOrderIdAndStatusNotOrderByIdDesc(order.getId(), "EXPIRED")
                .orElseThrow(() -> new BizException("作业单 " + order.getOrderNo() + " 的配药批次已失效，不能起飞"));
        if (!"LOCKED".equals(batch.getStatus())) {
            throw new BizException("作业单 " + order.getOrderNo() + " 的配药批次 " + batch.getBatchNo()
                    + " 当前为" + PlantRules.mixingStatusName(batch.getStatus()) + "，复核锁定后才能起飞");
        }
        List<String> diffs = snapshotDiffs(batch, order,
                droneRepository.findById(batch.getDroneIdSnapshot()).orElse(null));
        if (!diffs.isEmpty()) {
            throw new BizException("配药批次 " + batch.getBatchNo()
                    + " 的快照与当前作业口径不一致，不能起飞，具体不一致项：" + String.join("；", diffs)
                    + "。请按新条件重新发起配药复核");
        }
        // 兜底再算一次分桶（正常路径锁定时已校验，这里防数据被外部改动）
        validateAgainst(batch, bucketsOf(batch.getId()));
    }

    /**
     * 失效联动：作业单取消、改派无人机，或计划亩数 / 亩用量 / 药剂配方变化时调用。
     * 已锁定批次立即失效、不可恢复，也不能复制为有效结果，只能按新条件重新发起。
     * 已起飞（WORKING/DONE）的单子不会走到这里。
     */
    @Transactional
    public void invalidateByJobOrder(Long jobOrderId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        List<MixingBatch> openBatches = new ArrayList<>();
        // 先逐行加锁再改，避免和复核人锁定动作并发交错
        for (MixingBatch batch : batchRepository.findByJobOrderIdOrderByIdDesc(jobOrderId)) {
            if (!"EXPIRED".equals(batch.getStatus())) {
                openBatches.add(lockBatch(batch.getId()));
            }
        }
        for (MixingBatch batch : openBatches) {
            batch.setStatus("EXPIRED");
            batch.setExpireReason(reason);
            batch.setUpdatedAt(now);
            batchRepository.save(batch);
            logReview(batch.getId(), "EXPIRE", null, reason, null, now);
        }
    }

    // ---------------- 校验与装配 ----------------

    /**
     * 分桶复核的统一口径：
     * 非空、每桶 > 0、每桶 ≤ 最大载荷（不能超载）、合计与计划总药量按 0.01L 精确一致。
     * 误差一律显式报出，不会自动补到最后一桶。
     */
    private List<BigDecimal> normalizeAndValidate(List<BigDecimal> raw, BigDecimal totalL, BigDecimal maxPayload) {
        BigDecimal total = money(totalL);
        BigDecimal payload = money(maxPayload);
        if (raw.isEmpty()) {
            throw new BizException("至少保留一个桶");
        }
        List<BigDecimal> amounts = new ArrayList<>(raw.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < raw.size(); i++) {
            BigDecimal v = raw.get(i);
            if (v == null) {
                throw new BizException("第 " + (i + 1) + " 桶药量为空，每桶都必须大于 0");
            }
            BigDecimal rounded = v.setScale(2, RoundingMode.HALF_UP);
            if (v.compareTo(rounded) != 0) {
                throw new BizException("第 " + (i + 1) + " 桶药量 " + v + " 超过 0.01L 的最小颗粒度");
            }
            v = rounded;
            if (v.signum() <= 0) {
                throw new BizException("第 " + (i + 1) + " 桶药量为 " + v + "，所有桶必须大于 0");
            }
            if (v.compareTo(payload) > 0) {
                throw new BizException("第 " + (i + 1) + " 桶药量 " + v + "L 超过该机最大载荷 "
                        + payload + "L，任何一桶都不能超载");
            }
            amounts.add(v);
            sum = sum.add(v);
        }
        if (sum.compareTo(total) != 0) {
            BigDecimal delta = sum.subtract(total);
            throw new BizException("各桶合计 " + sum + "L 与计划总药量 " + total + "L 不一致（相差 "
                    + (delta.signum() > 0 ? "+" : "") + delta + "L）；请调整每桶药量使其精确守恒，"
                    + "不能把误差留给最后一桶");
        }
        return amounts;
    }

    /** 用快照口径复核已保存的分桶（提交 / 锁定 / 起飞前都走这里）。 */
    private void validateAgainst(MixingBatch batch, List<MixingBucket> buckets) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (MixingBucket bucket : buckets) {
            amounts.add(bucket.getAmountL());
        }
        normalizeAndValidate(amounts, batch.getTotalL(), batch.getMaxPayloadSnapshot());
    }

    /** 快照与当前作业单 / 无人机载荷逐项比对，返回具体不一致项（空列表 = 一致）。 */
    private List<String> snapshotDiffs(MixingBatch batch, JobOrder order, Drone drone) {
        List<String> diffs = new ArrayList<>();
        if (!eq(batch.getOrderNoSnapshot(), order.getOrderNo())) {
            diffs.add("作业单号：快照 " + batch.getOrderNoSnapshot() + " / 当前 " + order.getOrderNo());
        }
        if (!eq(batch.getFormulaSnapshot(), order.getFormula())) {
            diffs.add("药剂配方：快照「" + batch.getFormulaSnapshot() + "」/ 当前「" + order.getFormula() + "」");
        }
        if (money(batch.getPlannedMuSnapshot()).compareTo(money(order.getPlannedMu())) != 0) {
            diffs.add("计划亩数：快照 " + batch.getPlannedMuSnapshot() + " 亩 / 当前 " + order.getPlannedMu() + " 亩");
        }
        if (money(batch.getDosePerMuSnapshot()).compareTo(money(order.getDosePerMu())) != 0) {
            diffs.add("亩用量：快照 " + batch.getDosePerMuSnapshot() + " L/亩 / 当前 "
                    + order.getDosePerMu() + " L/亩");
        }
        if (!batch.getDroneIdSnapshot().equals(order.getDroneId())) {
            diffs.add("无人机：批次按 " + batch.getDroneBodyNoSnapshot()
                    + " 配药，作业单已改派为机身 id=" + order.getDroneId());
        } else if (drone != null
                && money(batch.getMaxPayloadSnapshot()).compareTo(money(drone.getMaxPayload())) != 0) {
            diffs.add("最大载荷：" + drone.getBodyNo() + " 快照 " + batch.getMaxPayloadSnapshot()
                    + "L / 当前 " + drone.getMaxPayload() + "L");
        }
        return diffs;
    }

    private void snapshotFrom(MixingBatch batch, JobOrder order, Drone drone, BigDecimal total, int bucketCount) {
        batch.setOrderNoSnapshot(order.getOrderNo());
        batch.setFormulaSnapshot(order.getFormula());
        batch.setPlannedMuSnapshot(money(order.getPlannedMu()));
        batch.setDosePerMuSnapshot(money(order.getDosePerMu()));
        batch.setTotalL(total);
        batch.setDroneIdSnapshot(drone.getId());
        batch.setDroneBodyNoSnapshot(drone.getBodyNo());
        batch.setDroneModelSnapshot(drone.getModel());
        batch.setMaxPayloadSnapshot(money(drone.getMaxPayload()));
        batch.setBucketCount(bucketCount);
    }

    private void saveBuckets(Long batchId, List<BigDecimal> amounts) {
        int seq = 1;
        for (BigDecimal amount : amounts) {
            MixingBucket bucket = new MixingBucket();
            bucket.setBatchId(batchId);
            bucket.setSeqNo(seq++);
            bucket.setAmountL(amount);
            bucketRepository.save(bucket);
        }
    }

    private void logReview(Long batchId, String action, Pilot actor, String comment, String fallbackComment,
                           LocalDateTime now) {
        MixingReview review = new MixingReview();
        review.setBatchId(batchId);
        review.setAction(action);
        if (actor != null) {
            review.setActorPilotId(actor.getId());
            review.setActorName(actor.getName());
        }
        review.setComment(comment != null ? comment : fallbackComment);
        review.setCreatedAt(now);
        reviewRepository.save(review);
    }

    private Views.MixingBatchView toView(MixingBatch batch, NameBook book, boolean withDetail) {
        List<MixingBucket> bucketRows = withDetail ? bucketsOf(batch.getId()) : List.of();
        List<Views.MixingBucketView> buckets = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (MixingBucket row : bucketRows) {
            buckets.add(new Views.MixingBucketView(row.getSeqNo(), row.getAmountL(),
                    batch.getMaxPayloadSnapshot()));
            sum = sum.add(money(row.getAmountL()));
        }
        if (!withDetail) {
            // 列表页不查明细，合计直接展示快照总量，省一次查询
            sum = batch.getTotalL();
        }
        List<Views.MixingReviewView> reviews = new ArrayList<>();
        if (withDetail) {
            for (MixingReview row : reviewRepository.findByBatchIdOrderByIdAsc(batch.getId())) {
                reviews.add(new Views.MixingReviewView(row.getId(), row.getAction(),
                        PlantRules.reviewActionName(row.getAction()), row.getActorPilotId(),
                        row.getActorName(), row.getComment(), row.getCreatedAt()));
            }
        }
        JobOrder order = book.orders.get(batch.getJobOrderId());
        String plotNo = null;
        String farmerName = null;
        String jobDate = null;
        List<String> diffs = List.of();
        if (order != null) {
            PlotOption plot = book.plots.get(order.getPlotId());
            if (plot != null) {
                plotNo = plot.getPlotNo();
                farmerName = plot.getFarmerName();
            }
            jobDate = String.valueOf(order.getJobDate());
            diffs = snapshotDiffs(batch, order, book.drones.get(order.getDroneId()));
        }
        return new Views.MixingBatchView(
                batch.getId(), batch.getBatchNo(), batch.getJobOrderId(), batch.getOrderNoSnapshot(),
                plotNo, farmerName, jobDate,
                batch.getStatus(), PlantRules.mixingStatusName(batch.getStatus()),
                batch.getPreparedPilotId(), book.pilotName(batch.getPreparedPilotId()),
                book.pilotNo(batch.getPreparedPilotId()),
                batch.getReviewerId(),
                batch.getReviewerId() == null ? null : book.pilotName(batch.getReviewerId()),
                batch.getRejectReason(), batch.getExpireReason(),
                batch.getFormulaSnapshot(), batch.getPlannedMuSnapshot(), batch.getDosePerMuSnapshot(),
                batch.getTotalL(), batch.getDroneIdSnapshot(), batch.getDroneBodyNoSnapshot(),
                batch.getDroneModelSnapshot(), batch.getMaxPayloadSnapshot(), batch.getBucketCount(),
                money(sum), batch.getLockedAt(), batch.getSubmittedAt(), batch.getCreatedAt(),
                batch.getUpdatedAt(), diffs.isEmpty(), diffs, buckets, reviews);
    }

    private NameBook nameBook() {
        Map<Long, JobOrder> orders = new HashMap<>();
        for (JobOrder order : jobOrderRepository.findAll()) {
            orders.put(order.getId(), order);
        }
        Map<Long, Drone> drones = new HashMap<>();
        for (Drone drone : droneRepository.findAll()) {
            drones.put(drone.getId(), drone);
        }
        Map<Long, Pilot> pilots = new HashMap<>();
        for (Pilot pilot : pilotRepository.findAll()) {
            pilots.put(pilot.getId(), pilot);
        }
        Map<Long, PlotOption> plots = new HashMap<>();
        for (PlotOption plot : plotRepository.findAllByOrderByPlotNoAsc(PlotOption.class)) {
            plots.put(plot.getId(), plot);
        }
        return new NameBook(orders, drones, pilots, plots);
    }

    private List<MixingBucket> bucketsOf(Long batchId) {
        return bucketRepository.findByBatchIdOrderBySeqNoAsc(batchId);
    }

    private MixingBatch requireBatch(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new BizException("配药批次不存在：id=" + id));
    }

    private MixingBatch lockBatch(Long id) {
        return batchRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("配药批次不存在：id=" + id));
    }

    private JobOrder requireOrder(Long id) {
        return jobOrderRepository.findById(id)
                .orElseThrow(() -> new BizException("作业单不存在：id=" + id));
    }

    private Drone requireDrone(Long id) {
        return droneRepository.findById(id)
                .orElseThrow(() -> new BizException("无人机不存在：id=" + id));
    }

    private Pilot requireActivePilot(Long id, String role) {
        Pilot pilot = pilotRepository.findById(id)
                .orElseThrow(() -> new BizException(role + "不存在：id=" + id));
        if (!"ACTIVE".equals(pilot.getStatus())) {
            throw new BizException(pilot.getName() + " 当前为"
                    + PlantRules.pilotStatusName(pilot.getStatus()) + "，只有在队飞手才能担任" + role);
        }
        return pilot;
    }

    /** 复核人：另一名在队飞手 + 三级 / 教员执照。 */
    private Pilot requireReviewer(Long reviewerId, MixingBatch batch) {
        Pilot reviewer = requireActivePilot(reviewerId, "复核人");
        if (!PlantRules.reviewerLicenseAllowed(reviewer.getLicenseLevel())) {
            throw new BizException("复核人 " + reviewer.getName() + " 的执照为"
                    + PlantRules.licenseName(reviewer.getLicenseLevel()) + "，复核人必须是三级（高级）或教员");
        }
        if (reviewer.getId().equals(batch.getPreparedPilotId())) {
            throw new BizException("复核人必须是配药人之外的另一名飞手，" + reviewer.getName()
                    + " 是本批次的配药人，不能自配自核");
        }
        return reviewer;
    }

    /** 已起飞 / 已取消的作业单与已失效批次：不允许再修改、提交、驳回或复核。 */
    private void requireOrderPending(MixingBatch batch) {
        if ("EXPIRED".equals(batch.getStatus())) {
            throw new BizException("批次 " + batch.getBatchNo() + " 已失效（"
                    + batch.getExpireReason() + "），不可恢复或继续处理，只能按新条件重新发起配药");
        }
        JobOrder order = requireOrder(batch.getJobOrderId());
        if ("WORKING".equals(order.getStatus()) || "DONE".equals(order.getStatus())) {
            throw new BizException("作业单 " + order.getOrderNo() + " 已经起飞（"
                    + PlantRules.orderStatusName(order.getStatus())
                    + "），不允许再新建、修改、驳回或复核配药批次");
        }
        if ("CANCELED".equals(order.getStatus())) {
            throw new BizException("作业单 " + order.getOrderNo() + " 已取消，请刷新查看最新批次状态");
        }
    }

    private BigDecimal totalChemical(JobOrder order) {
        return money(order.getPlannedMu()).multiply(money(order.getDosePerMu())).setScale(2, RoundingMode.HALF_UP);
    }

    private String pilotName(Long id) {
        return pilotRepository.findById(id).map(Pilot::getName).orElse("id=" + id);
    }

    private String nextBatchNo() {
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "PB-" + day;
        long seq = batchRepository.count() + 1;
        String candidate;
        do {
            candidate = prefix + String.format("%02d", seq);
            seq++;
        } while (batchRepository.findByBatchNo(candidate).isPresent());
        return candidate;
    }

    private String validStatus(String status) {
        if (!PlantRules.isValidMixingStatus(status)) {
            throw new BizException("未知配药批次状态：" + status);
        }
        return status;
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record NameBook(Map<Long, JobOrder> orders, Map<Long, Drone> drones, Map<Long, Pilot> pilots,
                            Map<Long, PlotOption> plots) {

        private String pilotName(Long id) {
            Pilot pilot = pilots.get(id);
            return pilot == null ? "id=" + id : pilot.getName();
        }

        private String pilotNo(Long id) {
            Pilot pilot = pilots.get(id);
            return pilot == null ? null : pilot.getPilotNo();
        }
    }
}
