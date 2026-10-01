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
import com.drone.plant.dto.HandlingDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.service.AbnormalHandlingService;

import jakarta.validation.Valid;

/** 模块五-延：异常架次停飞处置。 */
@RestController
@RequestMapping("/api/abnormal-handlings")
public class AbnormalHandlingController {

    private final AbnormalHandlingService handlingService;

    public AbnormalHandlingController(AbnormalHandlingService handlingService) {
        this.handlingService = handlingService;
    }

    /** 处置台列表：按状态 / 机身筛选，不传查全部 */
    @GetMapping
    public ApiResponse<List<Views.HandlingView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "droneId", required = false) Long droneId) {
        return ApiResponse.success(handlingService.list(status, droneId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Views.HandlingView> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(handlingService.detail(id));
    }

    /** 值班主管下拉：在队且三级 / 教员 */
    @GetMapping("/supervisor-options")
    public ApiResponse<List<Views.SupervisorOptionView>> supervisorOptions() {
        return ApiResponse.success(handlingService.supervisorOptions());
    }

    /** 继续观察 */
    @PostMapping("/{id}/observe")
    public ApiResponse<Views.HandlingView> observe(@PathVariable("id") Long id,
                                                   @Valid @RequestBody HandlingDtos.ActReq req) {
        return ApiResponse.success("已登记继续观察，机身状态不变",
                handlingService.observe(id, req.getSupervisorId(), req.getComment()));
    }

    /** 停飞机身：关联无人机立即转维护，待飞作业单须改派 */
    @PostMapping("/{id}/ground")
    public ApiResponse<Views.HandlingView> ground(@PathVariable("id") Long id,
                                                  @Valid @RequestBody HandlingDtos.ActReq req) {
        return ApiResponse.success("已停飞，机身转入维护；待飞作业单须改派",
                handlingService.ground(id, req.getSupervisorId(), req.getComment()));
    }

    /** 解除：必须填写处置结论，该机身无其他未结束处置时恢复在库 */
    @PostMapping("/{id}/release")
    public ApiResponse<Views.HandlingView> release(@PathVariable("id") Long id,
                                                   @Valid @RequestBody HandlingDtos.ActReq req) {
        return ApiResponse.success("处置已解除，结论已留档",
                handlingService.release(id, req.getSupervisorId(), req.getComment()));
    }
}
