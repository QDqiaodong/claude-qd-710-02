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
import com.drone.plant.dto.PilotDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.service.PilotService;

import jakarta.validation.Valid;

/** 模块三：飞手。 */
@RestController
@RequestMapping("/api/pilots")
public class PilotController {

    private final PilotService pilotService;

    public PilotController(PilotService pilotService) {
        this.pilotService = pilotService;
    }

    @GetMapping
    public ApiResponse<List<Views.PilotView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "licenseLevel", required = false) String licenseLevel) {
        return ApiResponse.success(pilotService.list(status, licenseLevel));
    }

    /** 派工下拉框 */
    @GetMapping("/options")
    public ApiResponse<List<Views.PilotOptionView>> options() {
        return ApiResponse.success(pilotService.options());
    }

    @PostMapping
    public ApiResponse<Views.PilotView> create(@Valid @RequestBody PilotDtos.SaveReq req) {
        return ApiResponse.success("飞手已入队", pilotService.create(req));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Views.PilotView> status(@PathVariable("id") Long id,
                                               @Valid @RequestBody PilotDtos.StatusReq req) {
        return ApiResponse.success("飞手状态已更新", pilotService.updateStatus(id, req.getStatus()));
    }
}
