package com.drone.plant.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drone.plant.common.ApiResponse;
import com.drone.plant.dto.DroneDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.service.DroneService;
import com.drone.plant.service.JobOrderService;

import jakarta.validation.Valid;

/** 模块二：植保无人机。 */
@RestController
@RequestMapping("/api/drones")
public class DroneController {

    private final DroneService droneService;
    private final JobOrderService jobOrderService;

    public DroneController(DroneService droneService, JobOrderService jobOrderService) {
        this.droneService = droneService;
        this.jobOrderService = jobOrderService;
    }

    @GetMapping
    public ApiResponse<List<Views.DroneView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "droneType", required = false) String droneType) {
        return ApiResponse.success(droneService.list(status, droneType));
    }

    /** 派机下拉框 */
    @GetMapping("/options")
    public ApiResponse<List<Views.DroneOptionView>> options() {
        return ApiResponse.success(droneService.options());
    }

    @PostMapping
    public ApiResponse<Views.DroneView> create(@Valid @RequestBody DroneDtos.SaveReq req) {
        return ApiResponse.success("无人机已入库", droneService.create(req));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Views.DroneView> status(@PathVariable("id") Long id,
                                               @Valid @RequestBody DroneDtos.StatusReq req) {
        return ApiResponse.success("无人机状态已切换", droneService.updateStatus(id, req.getStatus()));
    }

    /** 卡片墙上的「派机」：给这台机配地配人，直接开出一张待飞作业单 */
    @PostMapping("/{id}/dispatch")
    public ApiResponse<Views.JobOrderView> dispatch(@PathVariable("id") Long id,
                                                    @Valid @RequestBody DroneDtos.DispatchReq req) {
        return ApiResponse.success("派机成功，作业单已生成", jobOrderService.dispatch(id, req));
    }
}
