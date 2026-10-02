package com.drone.plant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.drone.plant.common.BizException;
import com.drone.plant.dto.JobOrderDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.Drone;
import com.drone.plant.entity.JobOrder;
import com.drone.plant.entity.Plot;
import com.drone.plant.projection.DroneOption;
import com.drone.plant.projection.FlightLogStat;
import com.drone.plant.projection.JobOrderRow;
import com.drone.plant.projection.PilotOption;
import com.drone.plant.projection.PlotOption;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.FlightLogRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.MixingBatchRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;

/**
 * 完成口径单测：实际亩数只取已落库飞行日志汇总，手工值与汇总不一致必须拒绝。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobOrderDoneServiceTest {

    @Mock
    private JobOrderRepository jobOrderRepository;
    @Mock
    private PlotRepository plotRepository;
    @Mock
    private DroneRepository droneRepository;
    @Mock
    private PilotRepository pilotRepository;
    @Mock
    private FlightLogRepository flightLogRepository;
    @Mock
    private MixingBatchRepository mixingBatchRepository;
    @Mock
    private MixingService mixingService;
    @Mock
    private AbnormalHandlingService handlingService;

    private JobOrderService service;

    private JobOrder workingOrder;

    @BeforeEach
    void setUp() {
        service = new JobOrderService(jobOrderRepository, plotRepository, droneRepository, pilotRepository,
                flightLogRepository, mixingBatchRepository, mixingService, handlingService);

        workingOrder = new JobOrder();
        workingOrder.setId(100L);
        workingOrder.setOrderNo("JO-TEST01");
        workingOrder.setPlotId(10L);
        workingOrder.setDroneId(20L);
        workingOrder.setPilotId(30L);
        workingOrder.setStatus("WORKING");
        workingOrder.setPlannedMu(new BigDecimal("100.00"));
        workingOrder.setActualMu(BigDecimal.ZERO);
        workingOrder.setJobDate(LocalDate.of(2026, 9, 28));

        Plot plot = buildPlot();
        PlotOption plotOption = buildPlotOption();
        Drone drone = buildDrone();
        DroneOption droneOption = buildDroneOption();
        JobOrderRow resultRow = buildResultRow();

        when(jobOrderRepository.findById(100L)).thenReturn(java.util.Optional.of(workingOrder));
        when(jobOrderRepository.save(any(JobOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobOrderRepository.findAllByOrderByJobDateDesc(eq(JobOrderRow.class)))
                .thenReturn(List.of(resultRow));

        when(plotRepository.findById(10L)).thenReturn(java.util.Optional.of(plot));
        when(plotRepository.findAllByOrderByPlotNoAsc(eq(PlotOption.class)))
                .thenReturn(List.of(plotOption));
        when(droneRepository.findById(20L)).thenReturn(java.util.Optional.of(drone));
        when(droneRepository.findAllByOrderByBodyNoAsc(eq(DroneOption.class)))
                .thenReturn(List.of(droneOption));
        when(pilotRepository.findAllByOrderByPilotNoAsc(eq(PilotOption.class)))
                .thenReturn(Collections.emptyList());
        when(flightLogRepository.findAllByOrderByTakeoffAtDesc(eq(FlightLogStat.class)))
                .thenAnswer(inv -> currentLogs);
        when(mixingBatchRepository.findAll()).thenReturn(Collections.emptyList());
        when(handlingService.hasOpenHandling(20L)).thenReturn(false);
    }

    /** nameBook 汇总用：与每个用例桩给 findByJobOrderId 的架次保持同一份，视图里的 loggedMu 才对得上 */
    private List<FlightLogStat> currentLogs = Collections.emptyList();

    @Test
    void rejectsWhenFlightLogSummaryIsZeroAndRequestHasNoActualMu() {
        currentLogs = Collections.emptyList();
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(100L), eq(FlightLogStat.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.done(100L, new JobOrderDtos.DoneReq()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("汇总亩数为 0")
                .hasMessageContaining("不能完成");

        verify(jobOrderRepository, never()).save(any(JobOrder.class));
        verify(plotRepository, never()).save(any(Plot.class));
        verify(droneRepository, never()).save(any(Drone.class));
    }

    @Test
    void rejectsWhenZeroSummaryButClientSubmitsLargerActualMu() {
        currentLogs = Collections.emptyList();
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(100L), eq(FlightLogStat.class)))
                .thenReturn(Collections.emptyList());
        JobOrderDtos.DoneReq req = new JobOrderDtos.DoneReq();
        req.setActualMu(new BigDecimal("80"));

        assertThatThrownBy(() -> service.done(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("提交的是 80.00 亩")
                .hasMessageContaining("飞行日志汇总是 0.00 亩");

        verify(jobOrderRepository, never()).save(any(JobOrder.class));
    }

    @Test
    void rejectsWhenSubmittedActualMuDiffersFromLogSummary() {
        currentLogs = List.of(logStat("30.00"), logStat("22.00"));
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(100L), eq(FlightLogStat.class)))
                .thenReturn(currentLogs);
        JobOrderDtos.DoneReq req = new JobOrderDtos.DoneReq();
        req.setActualMu(new BigDecimal("120.00"));

        assertThatThrownBy(() -> service.done(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("提交的是 120.00 亩")
                .hasMessageContaining("飞行日志汇总是 52.00 亩")
                .hasMessageContaining("不一致");

        verify(jobOrderRepository, never()).save(any(JobOrder.class));
    }

    @Test
    void completesWithLoggedSummaryWhenRequestCarriesSameValue() {
        currentLogs = List.of(logStat("30.00"), logStat("22.00"));
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(100L), eq(FlightLogStat.class)))
                .thenReturn(currentLogs);
        JobOrderDtos.DoneReq req = new JobOrderDtos.DoneReq();
        req.setActualMu(new BigDecimal("52.00"));

        Views.JobOrderView view = service.done(100L, req);

        assertThat(view.status()).isEqualTo("DONE");
        assertThat(view.actualMu()).isEqualByComparingTo("52.00");
        assertThat(view.loggedMu()).isEqualByComparingTo("52.00");
        assertThat(view.finishedAt()).isNotNull();
        verify(plotRepository).save(any(Plot.class));
        verify(droneRepository).save(any(Drone.class));
    }

    @Test
    void completesWithLoggedSummaryWhenRequestOmitsActualMu() {
        currentLogs = List.of(logStat("26.00"), logStat("26.50"));
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(100L), eq(FlightLogStat.class)))
                .thenReturn(currentLogs);

        Views.JobOrderView view = service.done(100L, null);

        assertThat(view.actualMu()).isEqualByComparingTo("52.50");
        assertThat(view.loggedMu()).isEqualByComparingTo("52.50");
    }

    @Test
    void repeatedFinishOnDoneOrderDoesNotChangeStatsAgain() {
        currentLogs = List.of(logStat("26.00"), logStat("26.00"));
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(100L), eq(FlightLogStat.class)))
                .thenReturn(currentLogs);
        // 第一次完成
        service.done(100L, null);
        assertThat(workingOrder.getStatus()).isEqualTo("DONE");

        // 已完成是终态：再次提交（即使带不同亩数）也被状态机挡下，统计不动
        JobOrderDtos.DoneReq second = new JobOrderDtos.DoneReq();
        second.setActualMu(new BigDecimal("999"));
        assertThatThrownBy(() -> service.done(100L, second))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已完成");
        assertThat(workingOrder.getActualMu()).isEqualByComparingTo("52.00");
    }

    @Test
    void rejectsWhenLoggedSummaryExceedsPlannedTolerance() {
        // 计划 100 亩，1.2 倍上限 120；日志汇总 121 → 拒绝
        currentLogs = List.of(logStat("60.00"), logStat("61.00"));
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(100L), eq(FlightLogStat.class)))
                .thenReturn(currentLogs);

        assertThatThrownBy(() -> service.done(100L, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("1.2 倍");
        verify(jobOrderRepository, never()).save(any(JobOrder.class));
    }

    private FlightLogStat logStat(String area) {
        FlightLogStat stat = org.mockito.Mockito.mock(FlightLogStat.class);
        when(stat.getAreaMu()).thenReturn(new BigDecimal(area));
        when(stat.getChemicalL()).thenReturn(new BigDecimal("30.00"));
        when(stat.getJobOrderId()).thenReturn(100L);
        return stat;
    }

    private Plot buildPlot() {
        Plot plot = new Plot();
        plot.setId(10L);
        plot.setPlotNo("P-10");
        plot.setStatus("WORKING");
        plot.setAreaMu(new BigDecimal("110.00"));
        return plot;
    }

    private PlotOption buildPlotOption() {
        PlotOption option = org.mockito.Mockito.mock(PlotOption.class);
        when(option.getId()).thenReturn(10L);
        when(option.getPlotNo()).thenReturn("P-10");
        when(option.getFarmerName()).thenReturn("测试农户");
        when(option.getAreaMu()).thenReturn(new BigDecimal("110.00"));
        return option;
    }

    private Drone buildDrone() {
        Drone drone = new Drone();
        drone.setId(20L);
        drone.setBodyNo("DP-20");
        drone.setStatus("WORKING");
        drone.setTotalAreaMu(new BigDecimal("1000.00"));
        drone.setTotalFlightHours(new BigDecimal("100.00"));
        return drone;
    }

    private DroneOption buildDroneOption() {
        DroneOption option = org.mockito.Mockito.mock(DroneOption.class);
        when(option.getId()).thenReturn(20L);
        when(option.getBodyNo()).thenReturn("DP-20");
        when(option.getModel()).thenReturn("测试机型");
        when(option.getStatus()).thenReturn("WORKING");
        return option;
    }

    private JobOrderRow buildResultRow() {
        JobOrderRow row = org.mockito.Mockito.mock(JobOrderRow.class);
        when(row.getId()).thenReturn(workingOrder.getId());
        when(row.getOrderNo()).thenReturn(workingOrder.getOrderNo());
        when(row.getPlotId()).thenReturn(workingOrder.getPlotId());
        when(row.getDroneId()).thenReturn(workingOrder.getDroneId());
        when(row.getPilotId()).thenReturn(workingOrder.getPilotId());
        when(row.getFormula()).thenReturn("test formula");
        when(row.getDosePerMu()).thenReturn(new BigDecimal("1.20"));
        when(row.getPlannedMu()).thenReturn(workingOrder.getPlannedMu());
        when(row.getActualMu()).thenAnswer(inv -> workingOrder.getActualMu());
        when(row.getJobDate()).thenReturn(workingOrder.getJobDate());
        when(row.getStatus()).thenAnswer(inv -> workingOrder.getStatus());
        when(row.getFinishedAt()).thenAnswer(inv -> workingOrder.getFinishedAt());
        return row;
    }
}
