package com.drone.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 出参视图：全部用 record —— 这些对象只在 service 里构造、直接序列化给前端，不需要 setter。
 */
public final class Views {

    private Views() {
    }

    /** 地块台账 + 作业汇总（作业地图卡片与左侧列表用） */
    public record PlotView(
            Long id,
            String plotNo,
            String farmerName,
            String cropType,
            String cropTypeName,
            BigDecimal areaMu,
            String villageGroup,
            String terrain,
            String terrainName,
            String irrigation,
            String irrigationName,
            String status,
            String statusName,
            long orderCount,
            BigDecimal doneMu,
            LocalDate lastJobDate,
            LocalDateTime updatedAt) {
    }

    /** 地块下的作业单摘要（抽屉里的作业单列表用） */
    public record PlotOrderItem(
            Long id,
            String orderNo,
            LocalDate jobDate,
            String status,
            String statusName,
            BigDecimal plannedMu,
            BigDecimal actualMu,
            String droneBodyNo,
            String pilotName,
            int sortCount,
            LocalDateTime finishedAt) {
    }

    /** 无人机台账（卡片墙用） */
    public record DroneView(
            Long id,
            String bodyNo,
            String model,
            String droneType,
            String droneTypeName,
            BigDecimal maxPayload,
            BigDecimal sprayWidth,
            BigDecimal totalFlightHours,
            BigDecimal totalAreaMu,
            Integer batteryPacks,
            String status,
            String statusName,
            LocalDate purchaseDate,
            long openOrders,
            long pendingOrders,
            String currentPlotNo,
            long sortCount,
            long openHandlingCount,
            LocalDateTime updatedAt) {
    }

    /** 飞手台账（卡片墙用） */
    public record PilotView(
            Long id,
            String pilotNo,
            String name,
            String phone,
            String licenseLevel,
            String licenseName,
            String licenseColor,
            Integer totalSorts,
            BigDecimal totalAreaMu,
            String status,
            String statusName,
            long openOrders,
            String currentPlotNo,
            LocalDateTime updatedAt) {
    }

    /** 作业单（表格行用） */
    public record JobOrderView(
            Long id,
            String orderNo,
            Long plotId,
            String plotNo,
            String farmerName,
            Long droneId,
            String droneBodyNo,
            String droneModel,
            String droneStatus,
            String droneStatusName,
            boolean droneOpenHandling,
            Long pilotId,
            String pilotName,
            String formula,
            BigDecimal dosePerMu,
            BigDecimal plannedMu,
            BigDecimal actualMu,
            /** 已落库飞行日志累计亩数：完成口径以此为准，前端只读展示 */
            BigDecimal loggedMu,
            LocalDate jobDate,
            String weather,
            String status,
            String statusName,
            int progress,
            long sortCount,
            BigDecimal chemicalL,
            LocalDateTime finishedAt,
            LocalDateTime updatedAt,
            Long mixingBatchId,
            String mixingStatus,
            String mixingStatusName) {
    }

    /** 飞行日志（表格行用） */
    public record FlightLogView(
            Long id,
            Long jobOrderId,
            String orderNo,
            String plotNo,
            Long droneId,
            String droneBodyNo,
            String pilotName,
            Integer seqNo,
            LocalDateTime takeoffAt,
            LocalDateTime landingAt,
            Integer durationMin,
            BigDecimal areaMu,
            BigDecimal chemicalL,
            BigDecimal windSpeed,
            BigDecimal temperature,
            boolean abnormal,
            String abnormalDesc,
            Long handlingId,
            String handlingStatus,
            String handlingStatusName) {
    }

    /** 作业地图一块多边形：几何 + 着色 + 是否叠加喷洒航线 */
    public record PlotMapItem(
            Long id,
            String plotNo,
            String farmerName,
            String cropTypeName,
            BigDecimal areaMu,
            String status,
            String statusName,
            String color,
            boolean sprayed,
            String points,
            double cx,
            double cy,
            long sortCount,
            BigDecimal doneMu) {
    }

    /** 喷洒航线条纹的一组折返折线（前端直接画 <polyline>） */
    public record SprayTrack(Long plotId, String path, String color) {
    }

    /** 地块抽屉：地块详情 + 该地块的作业单列表 */
    public record PlotDetail(PlotView plot, List<PlotOrderItem> orders) {
    }

    /** 派工下拉框：地块瘦身形状（编号 + 承包户 + 作物 + 面积） */
    public record PlotOptionView(Long id, String plotNo, String farmerName, String cropTypeName,
                                 BigDecimal areaMu, String status, String statusName) {
    }

    /** 派工下拉框：无人机瘦身形状（机身编号 + 型号 + 状态 + 最大载荷） */
    public record DroneOptionView(Long id, String bodyNo, String model, String status, String statusName,
                                  BigDecimal maxPayload, boolean openHandling) {
    }

    /** 派工下拉框：飞手瘦身形状（编号 + 姓名 + 执照等级） */
    public record PilotOptionView(Long id, String pilotNo, String name, String licenseName, String status) {
    }

    /** 下拉框：作业单瘦身形状（单号 + 地块 + 日期 + 状态） */
    public record JobOrderOptionView(Long id, String orderNo, String plotNo, LocalDate jobDate,
                                     String status, String statusName) {
    }

    /** 飞行日志汇总条：总架次 / 总时长 / 总药量 / 异常架次 */
    public record LogSummary(long rows, long totalMinutes, BigDecimal totalAreaMu, BigDecimal chemicalTotal,
                             long abnormalRows) {
    }

    // ---------------- 模块五-延：异常架次停飞处置 ----------------

    /** 异常处置列表行 / 详情（详情多带架次快照、待飞单与留痕时间线，行视图这两项为空集合） */
    public record HandlingView(
            Long id,
            String handlingNo,
            String status,
            String statusName,
            Long flightLogId,
            Long jobOrderId,
            String orderNo,
            String plotNo,
            String pilotName,
            Integer seqNo,
            LocalDateTime takeoffAt,
            String abnormalDesc,
            Long droneId,
            String droneBodyNo,
            String droneModel,
            String droneStatus,
            String droneStatusName,
            Long registerPilotId,
            String registerName,
            Long supervisorId,
            String supervisorName,
            String observeComment,
            String groundComment,
            String conclusion,
            LocalDateTime registeredAt,
            LocalDateTime observedAt,
            LocalDateTime groundedAt,
            LocalDateTime closedAt,
            LocalDateTime updatedAt,
            /** 停飞时尚未起飞（待飞）的作业单：必须改派，不能再选用该机身 */
            List<HandlingPendingOrder> pendingOrders,
            /** 该机身其他仍未结束的处置（解除时用） */
            long otherOpenCount,
            List<HandlingActionView> actions) {
    }

    /** 处置单挂着的待飞作业单（改派提示用） */
    public record HandlingPendingOrder(Long id, String orderNo, String plotNo, LocalDate jobDate) {
    }

    /** 处置留痕时间线一条 */
    public record HandlingActionView(
            Long id,
            String action,
            String actionName,
            Long actorPilotId,
            String actorName,
            String comment,
            LocalDateTime createdAt) {
    }

    /** 值班主管下拉框：在队且三级（高级）/ 教员，与配药复核人同一门槛 */
    public record SupervisorOptionView(Long id, String pilotNo, String name, String licenseName) {
    }

    // ---------------- 模块六：作业前配药复核台 ----------------

    /** 配药批次列表行 / 详情（详情多带分桶与复核时间线，行视图这两列为空集合） */
    public record MixingBatchView(
            Long id,
            String batchNo,
            Long jobOrderId,
            String orderNo,
            String plotNo,
            String farmerName,
            String jobDate,
            String status,
            String statusName,
            Long preparedPilotId,
            String preparedPilotName,
            String preparedPilotNo,
            Long reviewerId,
            String reviewerName,
            String rejectReason,
            String expireReason,
            // 快照：复核人看得清本次配药依据
            String formulaSnapshot,
            BigDecimal plannedMuSnapshot,
            BigDecimal dosePerMuSnapshot,
            BigDecimal totalL,
            Long droneIdSnapshot,
            String droneBodyNoSnapshot,
            String droneModelSnapshot,
            BigDecimal maxPayloadSnapshot,
            int bucketCount,
            BigDecimal bucketSum,
            java.time.LocalDateTime lockedAt,
            java.time.LocalDateTime submittedAt,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt,
            // 快照与当前作业单 / 载荷是否仍一致（失效或口径变化时为 false，起飞会指出具体项）
            boolean snapshotConsistent,
            List<String> snapshotDiffs,
            List<MixingBucketView> buckets,
            List<MixingReviewView> reviews) {
    }

    /** 分桶行 */
    public record MixingBucketView(Integer seqNo, BigDecimal amountL, BigDecimal maxPayload) {
    }

    /** 复核时间线一条 */
    public record MixingReviewView(
            Long id,
            String action,
            String actionName,
            Long actorPilotId,
            String actorName,
            String comment,
            java.time.LocalDateTime createdAt) {
    }

    /** 作业单行上挂的当前配药批次摘要（作业单列表“配药”列直达用） */
    public record OrderMixingBrief(Long batchId, String batchNo, String status, String statusName) {
    }

    /** 飞控大屏总览计数器 */
    public record Overview(
            long plotTotal,
            long plotPending,
            long plotWorking,
            long plotDone,
            long plotHold,
            BigDecimal plotAreaTotal,
            long droneTotal,
            long droneIdle,
            long droneWorking,
            long droneMaint,
            long droneDisabled,
            BigDecimal droneAreaTotal,
            BigDecimal droneHourTotal,
            long pilotTotal,
            long pilotActive,
            long pilotLeave,
            long pilotInstructor,
            long orderPending,
            long orderWorking,
            long orderDone,
            long orderCanceled,
            BigDecimal plannedMuTotal,
            BigDecimal actualMuTotal,
            long logRows,
            long abnormalRows,
            long totalMinutes,
            BigDecimal chemicalTotal,
            long handlingPending,
            long handlingObserving,
            long handlingGrounded,
            long handlingOpen) {
    }
}
