package com.drone.plant.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drone.plant.common.ApiResponse;
import com.drone.plant.dto.Views;
import com.drone.plant.service.FarmService;

/** 首页：作业地图（服务端算好的多边形顶点）+ 飞控大屏总览。 */
@RestController
@RequestMapping("/api/farm")
public class FarmController {

    private final FarmService farmService;

    public FarmController(FarmService farmService) {
        this.farmService = farmService;
    }

    @GetMapping("/overview")
    public ApiResponse<Views.Overview> overview() {
        return ApiResponse.success(farmService.overview());
    }

    /** from / to 传了就按作业日期区间过滤。 */
    @GetMapping("/map")
    public ApiResponse<List<Views.PlotMapItem>> map(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(farmService.map(from, to));
    }
}
