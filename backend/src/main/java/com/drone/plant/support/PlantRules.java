package com.drone.plant.support;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 植保队的业务词典与飞行作业规则阈值。
 * 规则集中在此，service 只负责取数、校验与提示。
 */
public final class PlantRules {

    private PlantRules() {
    }

    // ---------------- 地块 ----------------
    private static final Map<String, String> CROP = Map.of(
            "RICE", "水稻",
            "WHEAT", "小麦",
            "CORN", "玉米",
            "COTTON", "棉花",
            "FRUIT", "果树",
            "OILSEED", "油菜");

    private static final Map<String, String> TERRAIN = Map.of(
            "PLAIN", "平原",
            "HILL", "丘陵",
            "MOUNTAIN", "山地");

    /** 地形要求的飞手执照：山地只能派高级及以上 */
    private static final Map<String, List<String>> TERRAIN_ALLOW_LEVEL = Map.of(
            "PLAIN", List.of("LEVEL1", "LEVEL2", "LEVEL3", "INSTRUCTOR"),
            "HILL", List.of("LEVEL2", "LEVEL3", "INSTRUCTOR"),
            "MOUNTAIN", List.of("LEVEL3", "INSTRUCTOR"));

    private static final Map<String, String> IRRIGATION = Map.of(
            "DITCH", "沟灌",
            "SPRINKLER", "喷灌",
            "DRIP", "滴灌",
            "RAINFED", "雨养");

    private static final Map<String, String> PLOT_STATUS = Map.of(
            "PENDING", "待作业",
            "WORKING", "作业中",
            "DONE", "已完成",
            "HOLD", "暂缓");

    /** 地块默认展示顺序：地图着色按这个顺序渲染 */
    public static final List<String> PLOT_STATUS_ORDER = List.of("PENDING", "WORKING", "DONE", "HOLD");

    // ---------------- 无人机 ----------------
    private static final Map<String, String> DRONE_TYPE = Map.of(
            "MULTI", "多旋翼",
            "SINGLE", "单旋翼",
            "SPRAY", "植保专用机");

    /** 各机型最大载荷上限（L）：机身越小载药越少 */
    private static final Map<String, BigDecimal> DRONE_MAX_PAYLOAD = Map.of(
            "MULTI", BigDecimal.valueOf(60),
            "SINGLE", BigDecimal.valueOf(50),
            "SPRAY", BigDecimal.valueOf(80));

    /** 各机型喷幅上限（m） */
    private static final Map<String, BigDecimal> DRONE_MAX_WIDTH = Map.of(
            "MULTI", BigDecimal.valueOf(9),
            "SINGLE", BigDecimal.valueOf(12),
            "SPRAY", BigDecimal.valueOf(10));

    private static final Map<String, String> DRONE_STATUS = Map.of(
            "IDLE", "在库",
            "WORKING", "作业中",
            "MAINT", "维修",
            "DISABLED", "停用");

    /** 单架次允许的最大连续飞行时长（分钟） */
    public static final int MAX_SORT_MINUTES = 30;

    /** 电池组数上下限 */
    public static final int MIN_BATTERY_PACKS = 1;
    public static final int MAX_BATTERY_PACKS = 20;

    // ---------------- 飞手 ----------------
    private static final Map<String, String> LICENSE = Map.of(
            "LEVEL1", "初级",
            "LEVEL2", "中级",
            "LEVEL3", "高级",
            "INSTRUCTOR", "教员");

    private static final Map<String, String> LICENSE_COLOR = Map.of(
            "LEVEL1", "#8d6e63",
            "LEVEL2", "#0277bd",
            "LEVEL3", "#7b1fa2",
            "INSTRUCTOR", "#c62828");

    private static final List<String> LICENSE_ORDER = List.of("LEVEL1", "LEVEL2", "LEVEL3", "INSTRUCTOR");

    private static final Map<String, String> PILOT_STATUS = Map.of(
            "ACTIVE", "在队",
            "LEAVE", "请假",
            "RESIGNED", "离职");

    // ---------------- 作业单 ----------------
    private static final Map<String, String> ORDER_STATUS = Map.of(
            "PENDING", "待飞",
            "WORKING", "作业中",
            "DONE", "已完成",
            "CANCELED", "已取消");

    /** 作业单状态流转表：每一步能走到哪些状态由这里决定 */
    private static final Map<String, List<String>> ORDER_FLOW = Map.of(
            "PENDING", List.of("WORKING", "CANCELED"),
            "WORKING", List.of("DONE", "CANCELED"),
            "DONE", List.of(),
            "CANCELED", List.of());

    /** 亩用量上限（L/亩），超过判定为药害风险 */
    public static final BigDecimal MAX_DOSE_PER_MU = BigDecimal.valueOf(3.00);

    /** 计划亩数相对地块面积的浮动上限（10% 余量，应对田埂误差） */
    public static final BigDecimal PLANNED_TOLERANCE = BigDecimal.valueOf(1.10);

    /** 实际亩数相对计划亩数的浮动上限 */
    public static final BigDecimal ACTUAL_TOLERANCE = BigDecimal.valueOf(1.20);

    // ---------------- 飞行日志 ----------------
    /** 风速告警阈值（m/s）：达到即必须标记为异常 */
    public static final BigDecimal WARN_WIND = BigDecimal.valueOf(5.0);

    /** 单架次作业亩数上限 */
    public static final BigDecimal MAX_SORT_AREA = BigDecimal.valueOf(80.0);

    // ---------------- 模块五-延：异常架次停飞处置 ----------------
    private static final Map<String, String> HANDLING_STATUS = Map.of(
            "PENDING", "待处置",
            "OBSERVING", "继续观察",
            "GROUNDED", "停飞中",
            "CLOSED", "已解除");

    /** 处置状态的展示顺序（处置台状态筛选与汇总条） */
    public static final List<String> HANDLING_STATUS_ORDER =
            List.of("PENDING", "OBSERVING", "GROUNDED", "CLOSED");

    /** 未结束的处置状态：机身存在其中任一处置时不得切回在库 */
    public static final List<String> HANDLING_OPEN_STATUSES = List.of("PENDING", "OBSERVING", "GROUNDED");

    /** 处置流转表：待处置 / 继续观察 / 停飞中 → 继续观察、停飞、解除；观察中允许再次追加观察意见 */
    private static final Map<String, List<String>> HANDLING_FLOW = Map.of(
            "PENDING", List.of("OBSERVING", "GROUNDED", "CLOSED"),
            "OBSERVING", List.of("OBSERVING", "GROUNDED", "CLOSED"),
            "GROUNDED", List.of("CLOSED"),
            "CLOSED", List.of());

    private static final Map<String, String> HANDLING_ACTION = Map.of(
            "REGISTER", "登记立案",
            "OBSERVE", "继续观察",
            "GROUND", "停飞机身",
            "RELEASE", "解除");

    // ---------------- 模块六：配药复核 ----------------
    private static final Map<String, String> MIXING_STATUS = Map.of(
            "DRAFT", "草稿",
            "PENDING_REVIEW", "待复核",
            "LOCKED", "已锁定",
            "REJECTED", "已驳回",
            "EXPIRED", "已失效");

    /** 配药批次状态的展示顺序（复核台状态筛选） */
    public static final List<String> MIXING_STATUS_ORDER =
            List.of("DRAFT", "PENDING_REVIEW", "LOCKED", "REJECTED", "EXPIRED");

    private static final Map<String, String> REVIEW_ACTION = Map.of(
            "CREATE", "发起配药",
            "SUBMIT", "提交复核",
            "REJECT", "驳回",
            "LOCK", "锁定",
            "EXPIRE", "系统失效");

    /** 有资格做配药复核人的执照：三级（高级）或教员 */
    public static final List<String> REVIEWER_LICENSES = List.of("LEVEL3", "INSTRUCTOR");

    /** 分桶药量最小颗粒度（L），合计守恒按 0.01L 精确比对 */
    public static final BigDecimal BUCKET_SCALE_UNIT = BigDecimal.valueOf(0.01);

    // ---------------- 取值方法 ----------------

    public static boolean isValidCropType(String value) {
        return value != null && CROP.containsKey(value);
    }

    public static String cropName(String value) {
        return CROP.getOrDefault(value, value);
    }

    public static boolean isValidTerrain(String value) {
        return value != null && TERRAIN.containsKey(value);
    }

    public static String terrainName(String value) {
        return TERRAIN.getOrDefault(value, value);
    }

    public static List<String> levelsForTerrain(String terrain) {
        return TERRAIN_ALLOW_LEVEL.getOrDefault(terrain, TERRAIN_ALLOW_LEVEL.get("PLAIN"));
    }

    public static boolean isValidIrrigation(String value) {
        return value != null && IRRIGATION.containsKey(value);
    }

    public static String irrigationName(String value) {
        return IRRIGATION.getOrDefault(value, value);
    }

    public static boolean isValidPlotStatus(String value) {
        return value != null && PLOT_STATUS.containsKey(value);
    }

    public static String plotStatusName(String value) {
        return PLOT_STATUS.getOrDefault(value, value);
    }

    public static boolean isValidDroneType(String value) {
        return value != null && DRONE_TYPE.containsKey(value);
    }

    public static String droneTypeName(String value) {
        return DRONE_TYPE.getOrDefault(value, value);
    }

    public static BigDecimal droneMaxPayload(String type) {
        return DRONE_MAX_PAYLOAD.getOrDefault(type, BigDecimal.valueOf(60));
    }

    public static BigDecimal droneMaxWidth(String type) {
        return DRONE_MAX_WIDTH.getOrDefault(type, BigDecimal.valueOf(9));
    }

    public static boolean isValidDroneStatus(String value) {
        return value != null && DRONE_STATUS.containsKey(value);
    }

    public static String droneStatusName(String value) {
        return DRONE_STATUS.getOrDefault(value, value);
    }

    public static boolean isValidLicenseLevel(String value) {
        return value != null && LICENSE.containsKey(value);
    }

    public static String licenseName(String value) {
        return LICENSE.getOrDefault(value, value);
    }

    public static String licenseColor(String value) {
        return LICENSE_COLOR.getOrDefault(value, "#607d8b");
    }

    public static int licenseRank(String value) {
        int index = LICENSE_ORDER.indexOf(value);
        return index < 0 ? 0 : index + 1;
    }

    public static boolean isValidPilotStatus(String value) {
        return value != null && PILOT_STATUS.containsKey(value);
    }

    public static String pilotStatusName(String value) {
        return PILOT_STATUS.getOrDefault(value, value);
    }

    public static boolean isValidOrderStatus(String value) {
        return value != null && ORDER_STATUS.containsKey(value);
    }

    public static String orderStatusName(String value) {
        return ORDER_STATUS.getOrDefault(value, value);
    }

    public static boolean orderFlowAllowed(String from, String to) {
        return ORDER_FLOW.getOrDefault(from, List.of()).contains(to);
    }

    public static boolean isValidMixingStatus(String value) {
        return value != null && MIXING_STATUS.containsKey(value);
    }

    public static String mixingStatusName(String value) {
        return MIXING_STATUS.getOrDefault(value, value);
    }

    public static String reviewActionName(String value) {
        return REVIEW_ACTION.getOrDefault(value, value);
    }

    /** 复核人门槛：在队且执照为三级（高级）或教员；调用方还要排除配药人本人 */
    public static boolean reviewerLicenseAllowed(String licenseLevel) {
        return REVIEWER_LICENSES.contains(licenseLevel);
    }

    /** 起飞到降落折算分钟数（服务端以表里的 duration_min 为准，这里仅做一致性核对） */
    public static long minutesBetween(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return Duration.between(from, to).toMinutes();
    }

    // ---------------- 异常处置取值方法 ----------------

    public static boolean isValidHandlingStatus(String value) {
        return value != null && HANDLING_STATUS.containsKey(value);
    }

    public static String handlingStatusName(String value) {
        return HANDLING_STATUS.getOrDefault(value, value);
    }

    public static boolean handlingFlowAllowed(String from, String to) {
        return HANDLING_FLOW.getOrDefault(from, List.of()).contains(to);
    }

    public static String handlingActionName(String value) {
        return HANDLING_ACTION.getOrDefault(value, value);
    }
}
