package com.drone.plant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.drone.plant.common.BizException;
import com.drone.plant.dto.JobOrderDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.Drone;
import com.drone.plant.entity.JobOrder;
import com.drone.plant.entity.Plot;
import com.drone.plant.projection.DroneOption;
import com.drone.plant.projection.FlightLogStat;
import com.drone.plant.projection.JobOrderRow;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.FlightLogRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.MixingBatchRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;

/**
 * 作业单完成口径：实际亩数只能取已落库飞行日志汇总。
 * 用纯 Mockito 隔离仓储，聚焦 {@link JobOrderService#done} 的收口校验与状态落库。
 */
class JobOrderFinishTest {

    private JobOrderRepository jobOrderRepository;
    private PlotRepository plotRepository;
    private DroneRepository droneRepository;
    private PilotRepository pilotRepository;
    private FlightLogRepository flightLogRepository;
    private MixingBatchRepository mixingBatchRepository;
    private JobOrderService service;

    @BeforeEach
    void setUp() {
        jobOrderRepository = mock(JobOrderRepository.class);
        plotRepository = mock(PlotRepository.class);
        droneRepository = mock(DroneRepository.class);
        pilotRepository = mock(PilotRepository.class);
        flightLogRepository = mock(FlightLogRepository.class);
        mixingBatchRepository = mock(MixingBatchRepository.class);
        MixingService mixingService = mock(MixingService.class);
        AbnormalHandlingService handlingService = mock(AbnormalHandlingService.class);
        service = new JobOrderService(jobOrderRepository, plotRepository, droneRepository, pilotRepository,
                flightLogRepository, mixingBatchRepository, mixingService, handlingService);
    }

    private JobOrder workingOrder(BigDecimal plannedMu) {
        JobOrder order = new JobOrder();
        order.setId(1L);
        order.setOrderNo("JO-TEST-01");
        order.setPlotId(10L);
        order.setDroneId(20L);
        order.setPilotId(30L);
        order.setPlannedMu(plannedMu);
        order.setActualMu(BigDecimal.ZERO);
        order.setJobDate(LocalDate.of(2026, 9, 23));
        order.setStatus("WORKING");
        return order;
    }

    private FlightLogStat stat(BigDecimal areaMu) {
        FlightLogStat stat = mock(FlightLogStat.class);
        when(stat.getJobOrderId()).thenReturn(1L);
        when(stat.getAreaMu()).thenReturn(areaMu);
        when(stat.getChemicalL()).thenReturn(BigDecimal.ZERO);
        when(stat.getDurationMin()).thenReturn(12);
        when(stat.getAbnormal()).thenReturn(false);
        return stat;
    }

    /** 先把 mock 建好，避免在 when(...).thenReturn(...) 参数里嵌套桩 */
    private List<FlightLogStat> stats(String... areas) {
        List<FlightLogStat> list = new java.util.ArrayList<>();
        for (String area : areas) {
            list.add(stat(new BigDecimal(area)));
        }
        return list;
    }

    /** 成功收口后 reload 要走一遍投影查询，把相关仓储都桩好 */
    private void stubReload(JobOrder order) {
        when(jobOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(jobOrderRepository.save(any(JobOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        Plot plot = new Plot();
        plot.setId(10L);
        plot.setPlotNo("P-01");
        plot.setAreaMu(new BigDecimal("100.00"));
        plot.setStatus("WORKING");
        when(plotRepository.findById(10L)).thenReturn(Optional.of(plot));
        when(plotRepository.findAllByOrderByPlotNoAsc(any())).thenReturn(List.of());

        Drone drone = new Drone();
        drone.setId(20L);
        drone.setBodyNo("DP-01");
        drone.setStatus("WORKING");
        when(droneRepository.findById(20L)).thenReturn(Optional.of(drone));

        DroneOption droneOption = mock(DroneOption.class);
        when(droneOption.getId()).thenReturn(20L);
        when(droneOption.getBodyNo()).thenReturn("DP-01");
        when(droneOption.getModel()).thenReturn("T50");
        when(droneOption.getStatus()).thenReturn("IDLE");
        when(droneRepository.findAllByOrderByBodyNoAsc(any())).thenReturn(List.of(droneOption));
        when(pilotRepository.findAllByOrderByPilotNoAsc(any())).thenReturn(List.of());
        when(mixingBatchRepository.findAll()).thenReturn(List.of());

        JobOrderRow row = mock(JobOrderRow.class);
        when(row.getId()).thenReturn(1L);
        when(row.getOrderNo()).thenReturn(order.getOrderNo());
        when(row.getPlotId()).thenReturn(10L);
        when(row.getDroneId()).thenReturn(20L);
        when(row.getPilotId()).thenReturn(30L);
        when(row.getFormula()).thenReturn("配方");
        when(row.getDosePerMu()).thenReturn(new BigDecimal("1.20"));
        when(row.getPlannedMu()).thenReturn(order.getPlannedMu());
        when(row.getActualMu()).thenAnswer(inv -> order.getActualMu());
        when(row.getJobDate()).thenReturn(order.getJobDate());
        when(row.getStatus()).thenAnswer(inv -> order.getStatus());
        when(row.getWeather()).thenReturn("晴");
        when(jobOrderRepository.findAllByOrderByJobDateDesc(eq(JobOrderRow.class))).thenReturn(List.of(row));
    }

    @Test
    void 不传亩数时按日志汇总完成() {
        JobOrder order = workingOrder(new BigDecimal("100.00"));
        List<FlightLogStat> logs = stats("30.00", "22.50");
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(1L), eq(FlightLogStat.class)))
                .thenReturn(logs);
        when(flightLogRepository.findAllByOrderByTakeoffAtDesc(eq(FlightLogStat.class)))
                .thenReturn(logs);
        stubReload(order);

        Views.JobOrderView view = service.done(1L, new JobOrderDtos.DoneReq());

        assertEquals("DONE", order.getStatus());
        assertEquals(0, new BigDecimal("52.50").compareTo(order.getActualMu()));
        assertEquals(0, new BigDecimal("52.50").compareTo(view.loggedMu()));
        assertEquals(0, new BigDecimal("52.50").compareTo(view.actualMu()));
    }

    @Test
    void 日志汇总为零时拒绝完成且不改任何状态() {
        JobOrder order = workingOrder(new BigDecimal("100.00"));
        when(jobOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(1L), eq(FlightLogStat.class)))
                .thenReturn(List.of());

        BizException ex = assertThrows(BizException.class,
                () -> service.done(1L, new JobOrderDtos.DoneReq()));
        assertTrue(ex.getMessage().contains("累计亩数为 0"), ex.getMessage());
        assertEquals("WORKING", order.getStatus());
        verify(jobOrderRepository, never()).save(any());
        verify(plotRepository, never()).save(any());
        verify(droneRepository, never()).save(any());
    }

    @Test
    void 请求携带更大亩数与汇总不一致时拒绝并提示两个数字() {
        JobOrder order = workingOrder(new BigDecimal("100.00"));
        when(jobOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        List<FlightLogStat> smallLogs = stats("10.00");
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(1L), eq(FlightLogStat.class)))
                .thenReturn(smallLogs);

        JobOrderDtos.DoneReq req = new JobOrderDtos.DoneReq();
        req.setActualMu(new BigDecimal("99.00"));
        BizException ex = assertThrows(BizException.class, () -> service.done(1L, req));
        assertTrue(ex.getMessage().contains("99.00"), ex.getMessage());
        assertTrue(ex.getMessage().contains("10.00"), ex.getMessage());
        assertTrue(ex.getMessage().contains("不一致"), ex.getMessage());
        assertEquals("WORKING", order.getStatus());
        verify(jobOrderRepository, never()).save(any());
    }

    @Test
    void 请求携带更小亩数同样拒绝() {
        JobOrder order = workingOrder(new BigDecimal("100.00"));
        when(jobOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        List<FlightLogStat> biggerLogs = stats("50.00");
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(1L), eq(FlightLogStat.class)))
                .thenReturn(biggerLogs);

        JobOrderDtos.DoneReq req = new JobOrderDtos.DoneReq();
        req.setActualMu(new BigDecimal("8.00"));
        BizException ex = assertThrows(BizException.class, () -> service.done(1L, req));
        assertTrue(ex.getMessage().contains("8.00"), ex.getMessage());
        assertTrue(ex.getMessage().contains("50.00"), ex.getMessage());
        verify(jobOrderRepository, never()).save(any());
    }

    @Test
    void 请求亩数与汇总一致时放行且落库值取汇总() {
        JobOrder order = workingOrder(new BigDecimal("100.00"));
        List<FlightLogStat> logs = stats("52.00");
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(1L), eq(FlightLogStat.class)))
                .thenReturn(logs);
        when(flightLogRepository.findAllByOrderByTakeoffAtDesc(eq(FlightLogStat.class)))
                .thenReturn(logs);
        stubReload(order);

        JobOrderDtos.DoneReq req = new JobOrderDtos.DoneReq();
        req.setActualMu(new BigDecimal("52.00"));
        service.done(1L, req);

        assertEquals("DONE", order.getStatus());
        assertEquals(0, new BigDecimal("52.00").compareTo(order.getActualMu()));
    }

    @Test
    void 已完成作业单重复提交被状态机拒绝且统计不变() {
        JobOrder order = workingOrder(new BigDecimal("100.00"));
        order.setStatus("DONE");
        order.setActualMu(new BigDecimal("52.00"));
        when(jobOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        JobOrderDtos.DoneReq req = new JobOrderDtos.DoneReq();
        req.setActualMu(new BigDecimal("888.00"));
        BizException ex = assertThrows(BizException.class, () -> service.done(1L, req));
        assertTrue(ex.getMessage().contains("已完成"), ex.getMessage());
        assertEquals(0, new BigDecimal("52.00").compareTo(order.getActualMu()));
        verify(jobOrderRepository, never()).save(any());
        verify(flightLogRepository, never()).findByJobOrderIdOrderBySeqNoAsc(any(), any());
    }

    @Test
    void 正常完成时地块转已完成机身回库亩数取汇总() {
        JobOrder order = workingOrder(new BigDecimal("100.00"));
        List<FlightLogStat> logs = stats("40.00");
        when(flightLogRepository.findByJobOrderIdOrderBySeqNoAsc(eq(1L), eq(FlightLogStat.class)))
                .thenReturn(logs);
        when(flightLogRepository.findAllByOrderByTakeoffAtDesc(eq(FlightLogStat.class)))
                .thenReturn(logs);
        stubReload(order);

        service.done(1L, null);

        ArgumentCaptor<JobOrder> orderCaptor = ArgumentCaptor.forClass(JobOrder.class);
        verify(jobOrderRepository).save(orderCaptor.capture());
        assertEquals("DONE", orderCaptor.getValue().getStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(orderCaptor.getValue().getActualMu()));

        ArgumentCaptor<Plot> plotCaptor = ArgumentCaptor.forClass(Plot.class);
        verify(plotRepository).save(plotCaptor.capture());
        assertEquals("DONE", plotCaptor.getValue().getStatus());

        ArgumentCaptor<Drone> droneCaptor = ArgumentCaptor.forClass(Drone.class);
        verify(droneRepository).save(droneCaptor.capture());
        assertEquals("IDLE", droneCaptor.getValue().getStatus());
    }
}
