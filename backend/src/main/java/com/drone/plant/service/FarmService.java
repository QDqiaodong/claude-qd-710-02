package com.drone.plant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drone.plant.dto.Views;
import com.drone.plant.projection.DronePayload;
import com.drone.plant.projection.FlightLogStat;
import com.drone.plant.projection.JobOrderBrief;
import com.drone.plant.projection.PlotOption;
import com.drone.plant.projection.PlotSummary;
import com.drone.plant.repository.AbnormalHandlingRepository;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.FlightLogRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;
import com.drone.plant.support.PlantRules;

/**
 * 作业地图与飞控大屏：把地块摆成 4×3 田块拼接图。
 *
 * <p>几何是服务端算的——相邻田块共用抖动后的顶点，所以拼得严丝合缝又不规则；
 * 前端只负责把 {@code points} 画成 {@code <polygon>}，并把已完成地块上的往复喷洒航线条纹裁进去。
 */
@Service
public class FarmService {

    /** 地图画布 */
    private static final double[] GRID_X = {40, 250, 470, 700, 860};
    private static final double[] GRID_Y = {40, 200, 360, 520, 590};

    private static final int COLS = 4;
    private static final int ROWS = 3;

    private static final Map<String, String> STATUS_COLOR = Map.of(
            "PENDING", "#cfd8dc",
            "WORKING", "#7b1fa2",
            "DONE", "#2e7d32",
            "HOLD", "#fff59d");

    private final PlotRepository plotRepository;
    private final JobOrderRepository jobOrderRepository;
    private final FlightLogRepository flightLogRepository;
    private final DroneRepository droneRepository;
    private final PilotRepository pilotRepository;
    private final AbnormalHandlingRepository handlingRepository;

    public FarmService(PlotRepository plotRepository, JobOrderRepository jobOrderRepository,
                       FlightLogRepository flightLogRepository, DroneRepository droneRepository,
                       PilotRepository pilotRepository, AbnormalHandlingRepository handlingRepository) {
        this.plotRepository = plotRepository;
        this.jobOrderRepository = jobOrderRepository;
        this.flightLogRepository = flightLogRepository;
        this.droneRepository = droneRepository;
        this.pilotRepository = pilotRepository;
        this.handlingRepository = handlingRepository;
    }

    /** 作业地图：一块多边形一行，含顶点、颜色、是否叠加喷洒航线条纹 */
    @Transactional(readOnly = true)
    public List<Views.PlotMapItem> map(LocalDate from, LocalDate to) {
        List<PlotSummary> plots = plotRepository.findAllByOrderByPlotNoAsc(PlotSummary.class);
        Map<Long, PlotStat> stats = stats(from, to);
        List<Views.PlotMapItem> items = new ArrayList<>();
        for (int i = 0; i < plots.size(); i++) {
            PlotSummary row = plots.get(i);
            PlotStat stat = stats.getOrDefault(row.getId(), new PlotStat(0, BigDecimal.ZERO, false));
            double[][] corners = tile(i % (COLS * ROWS));
            String color = STATUS_COLOR.getOrDefault(row.getStatus(), "#cfd8dc");
            items.add(new Views.PlotMapItem(row.getId(), row.getPlotNo(), row.getFarmerName(),
                    PlantRules.cropName(row.getCropType()), row.getAreaMu(), row.getStatus(),
                    PlantRules.plotStatusName(row.getStatus()), color, stat.sprayed, points(corners),
                    center(corners, true), center(corners, false), stat.sortCount, stat.doneMu.setScale(2,
                            RoundingMode.HALF_UP)));
        }
        return items;
    }

    /** 飞控大屏总览计数器 */
    @Transactional(readOnly = true)
    public Views.Overview overview() {
        long minutes = 0;
        long abnormal = 0;
        BigDecimal plotArea = BigDecimal.ZERO;
        BigDecimal droneArea = BigDecimal.ZERO;
        BigDecimal droneHours = BigDecimal.ZERO;
        BigDecimal plannedMu = BigDecimal.ZERO;
        BigDecimal actualMu = BigDecimal.ZERO;
        BigDecimal chemical = BigDecimal.ZERO;
        long rows = 0;
        for (PlotOption plot : plotRepository.findAllByOrderByPlotNoAsc(PlotOption.class)) {
            plotArea = plotArea.add(nvl(plot.getAreaMu()));
        }
        for (DronePayload drone : droneRepository.findAllByOrderByBodyNoAsc(DronePayload.class)) {
            droneArea = droneArea.add(nvl(drone.getTotalAreaMu()));
            droneHours = droneHours.add(nvl(drone.getTotalFlightHours()));
        }
        for (JobOrderBrief order : jobOrderRepository.findAllByOrderByJobDateDesc(JobOrderBrief.class)) {
            plannedMu = plannedMu.add(nvl(order.getPlannedMu()));
            actualMu = actualMu.add(nvl(order.getActualMu()));
        }
        for (FlightLogStat log : flightLogRepository.findAllByOrderByTakeoffAtDesc(FlightLogStat.class)) {
            rows++;
            minutes += log.getDurationMin() == null ? 0 : log.getDurationMin();
            chemical = chemical.add(nvl(log.getChemicalL()));
            if (Boolean.TRUE.equals(log.getAbnormal())) {
                abnormal++;
            }
        }
        long handlingPending = handlingRepository.countByStatusIn(List.of("PENDING"));
        long handlingObserving = handlingRepository.countByStatusIn(List.of("OBSERVING"));
        long handlingGrounded = handlingRepository.countByStatusIn(List.of("GROUNDED"));
        long handlingOpen = handlingRepository.countByStatusIn(PlantRules.HANDLING_OPEN_STATUSES);
        return new Views.Overview(
                plotRepository.count(),
                plotRepository.countByStatus("PENDING"),
                plotRepository.countByStatus("WORKING"),
                plotRepository.countByStatus("DONE"),
                plotRepository.countByStatus("HOLD"),
                plotArea.setScale(2, RoundingMode.HALF_UP),
                droneRepository.count(),
                droneRepository.countByStatus("IDLE"),
                droneRepository.countByStatus("WORKING"),
                droneRepository.countByStatus("MAINT"),
                droneRepository.countByStatus("DISABLED"),
                droneArea.setScale(2, RoundingMode.HALF_UP),
                droneHours.setScale(2, RoundingMode.HALF_UP),
                pilotRepository.count(),
                pilotRepository.countByStatus("ACTIVE"),
                pilotRepository.countByStatus("LEAVE"),
                pilotRepository.countByLicenseLevel("INSTRUCTOR"),
                jobOrderRepository.countByStatus("PENDING"),
                jobOrderRepository.countByStatus("WORKING"),
                jobOrderRepository.countByStatus("DONE"),
                jobOrderRepository.countByStatus("CANCELED"),
                plannedMu.setScale(2, RoundingMode.HALF_UP),
                actualMu.setScale(2, RoundingMode.HALF_UP),
                rows, abnormal, minutes, chemical.setScale(2, RoundingMode.HALF_UP),
                handlingPending, handlingObserving, handlingGrounded, handlingOpen);
    }

    // ---------------- 几何：4×3 田块拼接 ----------------

    /** 第 i 块田的四个顶点：相邻田块共用同一批抖动后的网格点 */
    private static double[][] tile(int index) {
        int row = index / COLS % ROWS;
        int col = index % COLS;
        return new double[][]{
                point(row, col),
                point(row, col + 1),
                point(row + 1, col + 1),
                point(row + 1, col)
        };
    }

    /** 网格点：只抖动内部点，边界点保持不动，保证整片田[pos]在画布内 */
    private static double[] point(int row, int col) {
        double x = GRID_X[col];
        double y = GRID_Y[row];
        boolean interior = row > 0 && row < ROWS && col > 0 && col < COLS;
        if (interior) {
            x += jitter(row, col, true);
            y += jitter(row, col, false);
        }
        return new double[]{x, y};
    }

    /** 确定性抖动（-14~14 px），每次刷新出来的田块形状都一样 */
    private static double jitter(int row, int col, boolean horizontal) {
        int hash = (row * 73856093) ^ (col * 19349663) ^ (horizontal ? 83492791 : 297121507);
        return (Math.abs(hash) % 29) - 14;
    }

    private static String points(double[][] corners) {
        StringBuilder sb = new StringBuilder();
        for (double[] p : corners) {
            sb.append(round(p[0])).append(',').append(round(p[1])).append(' ');
        }
        return sb.toString().trim();
    }

    private static double center(double[][] corners, boolean horizontal) {
        double sum = 0;
        for (double[] p : corners) {
            sum += horizontal ? p[0] : p[1];
        }
        return sum / corners.length;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    // ---------------- 内部工具 ----------------

    /** 每个地块在给定日期区间内的作业进度 */
    private Map<Long, PlotStat> stats(LocalDate from, LocalDate to) {
        List<JobOrderBrief> orders = (from != null && to != null)
                ? jobOrderRepository.findByJobDateBetweenOrderByJobDateDesc(from, to, JobOrderBrief.class)
                : jobOrderRepository.findAllByOrderByJobDateDesc(JobOrderBrief.class);
        Map<Long, Long> plotOfOrder = new HashMap<>();
        Map<Long, PlotStat> map = new HashMap<>();
        for (JobOrderBrief order : orders) {
            if (order.getPlotId() == null) {
                continue;
            }
            plotOfOrder.put(order.getId(), order.getPlotId());
            PlotStat old = map.getOrDefault(order.getPlotId(), new PlotStat(0, BigDecimal.ZERO, false));
            BigDecimal done = "DONE".equals(order.getStatus())
                    ? old.doneMu.add(nvl(order.getActualMu())) : old.doneMu;
            map.put(order.getPlotId(), new PlotStat(old.sortCount, done, old.sprayed || "DONE".equals(order.getStatus())));
        }
        for (FlightLogStat log : flightLogRepository.findAllByOrderByTakeoffAtDesc(FlightLogStat.class)) {
            Long plotId = plotOfOrder.get(log.getJobOrderId());
            if (plotId == null) {
                continue;
            }
            PlotStat old = map.get(plotId);
            map.put(plotId, new PlotStat(old.sortCount + 1, old.doneMu, old.sprayed));
        }
        return map;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record PlotStat(long sortCount, BigDecimal doneMu, boolean sprayed) {
    }
}
