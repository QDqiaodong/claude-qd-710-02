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
import com.drone.plant.dto.PlotDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.service.PlotService;

import jakarta.validation.Valid;

/** 模块一：地块。 */
@RestController
@RequestMapping("/api/plots")
public class PlotController {

    private final PlotService plotService;

    public PlotController(PlotService plotService) {
        this.plotService = plotService;
    }

    /** status 为空时取全量，cropType 可选 */
    @GetMapping
    public ApiResponse<List<Views.PlotView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "cropType", required = false) String cropType) {
        return ApiResponse.success(plotService.list(status, cropType));
    }

    /** 地块详情 + 名下作业单列表（地图抽屉用） */
    @GetMapping("/{id}/detail")
    public ApiResponse<Views.PlotDetail> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(plotService.detail(id));
    }

    /** 派工下拉框：同一查询方法取 PlotOption 形状 */
    @GetMapping("/options")
    public ApiResponse<List<Views.PlotOptionView>> options() {
        return ApiResponse.success(plotService.options());
    }

    @PostMapping
    public ApiResponse<Views.PlotView> create(@Valid @RequestBody PlotDtos.SaveReq req) {
        return ApiResponse.success("地块已建档", plotService.create(req));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Views.PlotView> status(@PathVariable("id") Long id,
                                              @Valid @RequestBody PlotDtos.StatusReq req) {
        return ApiResponse.success("地块状态已更新", plotService.updateStatus(id, req.getStatus()));
    }
}
