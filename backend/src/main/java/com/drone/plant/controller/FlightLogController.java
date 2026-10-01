package com.drone.plant.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drone.plant.common.ApiResponse;
import com.drone.plant.dto.FlightLogDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.service.FlightLogService;

import jakarta.validation.Valid;

/** 模块五：飞行日志。 */
@RestController
@RequestMapping("/api/flight-logs")
public class FlightLogController {

    private final FlightLogService flightLogService;

    public FlightLogController(FlightLogService flightLogService) {
        this.flightLogService = flightLogService;
    }

    @GetMapping
    public ApiResponse<List<Views.FlightLogView>> list(
            @RequestParam(value = "jobOrderId", required = false) Long jobOrderId,
            @RequestParam(value = "droneId", required = false) Long droneId) {
        return ApiResponse.success(flightLogService.list(jobOrderId, droneId));
    }

    @GetMapping("/abnormal")
    public ApiResponse<List<Views.FlightLogView>> abnormal(
            @RequestParam(value = "jobOrderId", required = false) Long jobOrderId,
            @RequestParam(value = "droneId", required = false) Long droneId) {
        return ApiResponse.success(flightLogService.abnormal(jobOrderId, droneId));
    }

    /** 汇总条：总架次 / 总时长 / 总亩数 / 总药量 / 异常架次 */
    @GetMapping("/summary")
    public ApiResponse<Views.LogSummary> summary(
            @RequestParam(value = "jobOrderId", required = false) Long jobOrderId) {
        return ApiResponse.success(flightLogService.summary(jobOrderId));
    }

    @PostMapping
    public ApiResponse<Views.FlightLogView> create(@Valid @RequestBody FlightLogDtos.SaveReq req) {
        return ApiResponse.success("架次已登记，累计数据已回填", flightLogService.create(req));
    }
}
