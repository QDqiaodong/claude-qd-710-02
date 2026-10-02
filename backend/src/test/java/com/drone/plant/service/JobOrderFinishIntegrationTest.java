package com.drone.plant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.drone.plant.common.BizException;
import com.drone.plant.dto.FlightLogDtos;
import com.drone.plant.dto.JobOrderDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.Drone;
import com.drone.plant.entity.JobOrder;
import com.drone.plant.entity.Pilot;
import com.drone.plant.entity.Plot;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;

/**
 * 完成口径的全链路持久化验证（H2）：
 * 走完「作业中 → 登记架次 → 完成 / 拒绝」后，重新查询作业单、地块、无人机、首页总览，
 * 确认各处数字只跟随已落库飞行日志汇总，且重复提交不会二次改变统计。
 */
@SpringBootTest
@Transactional
class JobOrderFinishIntegrationTest {

    @Autowired
    private JobOrderService jobOrderService;
    @Autowired
    private FlightLogService flightLogService;
    @Autowired
    private FarmService farmService;
    @Autowired
    private PlotService plotService;
    @Autowired
    private PlotRepository plotRepository;
    @Autowired
    private DroneRepository droneRepository;
    @Autowired
    private PilotRepository pilotRepository;
    @Autowired
    private JobOrderRepository jobOrderRepository;

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private Fixture fixture(BigDecimal plannedMu, String orderStatus) {
        int seq = SEQ.getAndIncrement();
        Plot plot = new Plot();
        plot.setPlotNo("PT" + seq);
        plot.setFarmerName("测试合作社");
        plot.setCropType("RICE");
        plot.setAreaMu(new BigDecimal("200.00"));
        plot.setVillageGroup("一组");
        plot.setTerrain("PLAIN");
        plot.setIrrigation("DITCH");
        plot.setStatus("WORKING");
        plot.setUpdatedAt(LocalDateTime.now());
        plot = plotRepository.saveAndFlush(plot);

        Drone drone = new Drone();
        drone.setBodyNo("DT" + seq);
        drone.setModel("T50");
        drone.setDroneType("MULTI");
        drone.setMaxPayload(new BigDecimal("40.00"));
        drone.setSprayWidth(new BigDecimal("7.00"));
        drone.setTotalFlightHours(BigDecimal.ZERO);
        drone.setTotalAreaMu(BigDecimal.ZERO);
        drone.setBatteryPacks(4);
        drone.setStatus("WORKING");
        drone.setUpdatedAt(LocalDateTime.now());
        drone = droneRepository.saveAndFlush(drone);

        Pilot pilot = new Pilot();
        pilot.setPilotNo("LT" + seq);
        pilot.setName("测试飞手");
        pilot.setPhone("13800000000");
        pilot.setLicenseLevel("LEVEL3");
        pilot.setTotalSorts(0);
        pilot.setTotalAreaMu(BigDecimal.ZERO);
        pilot.setStatus("ACTIVE");
        pilot.setUpdatedAt(LocalDateTime.now());
        pilot = pilotRepository.saveAndFlush(pilot);

        JobOrder order = new JobOrder();
        order.setOrderNo("JT" + seq);
        order.setPlotId(plot.getId());
        order.setDroneId(drone.getId());
        order.setPilotId(pilot.getId());
        order.setFormula("测试配方");
        order.setDosePerMu(new BigDecimal("1.20"));
        order.setPlannedMu(plannedMu);
        order.setJobDate(LocalDate.of(2026, 9, 23));
        order.setWeather("晴");
        order.setStatus(orderStatus);
        order.setActualMu(BigDecimal.ZERO);
        order.setUpdatedAt(LocalDateTime.now());
        order = jobOrderRepository.saveAndFlush(order);

        return new Fixture(plot, drone, pilot, order);
    }

    private FlightLogDtos.SaveReq sort(Long jobOrderId, String area, String chemical, int minute) {
        FlightLogDtos.SaveReq req = new FlightLogDtos.SaveReq();
        req.setJobOrderId(jobOrderId);
        LocalDateTime takeoff = LocalDateTime.of(2026, 9, 23, 6, 0).plusMinutes(minute);
        req.setTakeoffAt(takeoff);
        req.setLandingAt(takeoff.plusMinutes(12));
        req.setAreaMu(new BigDecimal(area));
        req.setChemicalL(new BigDecimal(chemical));
        req.setWindSpeed(new BigDecimal("2.0"));
        req.setTemperature(new BigDecimal("22.0"));
        req.setAbnormal(false);
        return req;
    }

    @Test
    void 完成后作业单地块无人机首页总览刷新一致() {
        Fixture f = fixture(new BigDecimal("100.00"), "WORKING");
        flightLogService.create(sort(f.order.getId(), "30.00", "30.00", 0));
        flightLogService.create(sort(f.order.getId(), "22.50", "22.50", 30));

        Views.JobOrderView done = jobOrderService.done(f.order.getId(), null);
        assertEquals("DONE", done.status());
        assertAmountEquals("52.50", done.loggedMu());
        assertAmountEquals("52.50", done.actualMu());

        // 作业单列表：计划/日志汇总列与状态
        Views.JobOrderView listed = jobOrderService.list("DONE", null, null, null, null).stream()
                .filter(o -> o.id().equals(f.order.getId())).findFirst().orElseThrow();
        assertEquals("DONE", listed.status());
        assertAmountEquals("52.50", listed.loggedMu());
        assertAmountEquals("52.50", listed.actualMu());
        assertEquals(2, listed.sortCount());

        // 地块：已完成、累计亩数与日志汇总一致
        Views.PlotView plot = plotService.list(null, null).stream()
                .filter(p -> p.id().equals(f.plot.getId())).findFirst().orElseThrow();
        assertEquals("DONE", plot.status());
        assertAmountEquals("52.50", plot.doneMu());

        // 无人机：回库在库，累计亩数随两架次回填
        Drone drone = droneRepository.findById(f.drone.getId()).orElseThrow();
        assertEquals("IDLE", drone.getStatus());
        assertAmountEquals("52.50", drone.getTotalAreaMu());

        // 首页总览的实际亩数合计包含本单日志汇总
        Views.Overview overview = farmService.overview();
        BigDecimal actualOfOrder = jobOrderRepository.findById(f.order.getId()).orElseThrow().getActualMu();
        assertAmountEquals("52.50", actualOfOrder);
        assertTrue(overview.actualMuTotal().compareTo(new BigDecimal("52.50")) >= 0);
    }

    @Test
    void 零日志汇总拒绝完成且各处状态数字不变() {
        Fixture f = fixture(new BigDecimal("100.00"), "WORKING");

        BizException ex = assertThrows(BizException.class,
                () -> jobOrderService.done(f.order.getId(), null));
        assertTrue(ex.getMessage().contains("累计亩数为 0"), ex.getMessage());

        // 作业单仍作业中、实际亩数 0；地块仍作业中；机身仍作业中
        assertEquals("WORKING", jobOrderRepository.findById(f.order.getId()).orElseThrow().getStatus());
        assertEquals("WORKING", plotRepository.findById(f.plot.getId()).orElseThrow().getStatus());
        assertEquals("WORKING", droneRepository.findById(f.drone.getId()).orElseThrow().getStatus());
    }

    @Test
    void 携带不一致亩数拒绝并提示两个数字() {
        Fixture f = fixture(new BigDecimal("100.00"), "WORKING");
        flightLogService.create(sort(f.order.getId(), "10.00", "10.00", 0));

        JobOrderDtos.DoneReq req = new JobOrderDtos.DoneReq();
        req.setActualMu(new BigDecimal("99.00"));
        BizException ex = assertThrows(BizException.class, () -> jobOrderService.done(f.order.getId(), req));
        assertTrue(ex.getMessage().contains("99.00") && ex.getMessage().contains("10.00"), ex.getMessage());
        assertEquals("WORKING", jobOrderRepository.findById(f.order.getId()).orElseThrow().getStatus());
    }

    @Test
    void 完成后重复提交不二次改变统计() {
        Fixture f = fixture(new BigDecimal("100.00"), "WORKING");
        flightLogService.create(sort(f.order.getId(), "40.00", "40.00", 0));
        jobOrderService.done(f.order.getId(), null);

        BigDecimal actualAfterDone = jobOrderRepository.findById(f.order.getId()).orElseThrow().getActualMu();
        BigDecimal droneAfterDone = droneRepository.findById(f.drone.getId()).orElseThrow().getTotalAreaMu();

        // 重复提交：不带亩数、带一致亩数、带更大亩数都必须被状态机拒绝
        assertThrows(BizException.class, () -> jobOrderService.done(f.order.getId(), null));
        JobOrderDtos.DoneReq same = new JobOrderDtos.DoneReq();
        same.setActualMu(new BigDecimal("40.00"));
        assertThrows(BizException.class, () -> jobOrderService.done(f.order.getId(), same));
        JobOrderDtos.DoneReq bigger = new JobOrderDtos.DoneReq();
        bigger.setActualMu(new BigDecimal("999.00"));
        assertThrows(BizException.class, () -> jobOrderService.done(f.order.getId(), bigger));

        assertEquals(0, actualAfterDone.compareTo(
                jobOrderRepository.findById(f.order.getId()).orElseThrow().getActualMu()));
        assertEquals(0, droneAfterDone.compareTo(
                droneRepository.findById(f.drone.getId()).orElseThrow().getTotalAreaMu()));
    }

    private static void assertAmountEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + " 实际 " + actual);
    }

    private record Fixture(Plot plot, Drone drone, Pilot pilot, JobOrder order) {
    }
}
