package com.drone.plant.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drone.plant.common.ApiResponse;
import com.drone.plant.dto.JobOrderDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.service.JobOrderService;

import jakarta.validation.Valid;

/** 模块四：作业单。 */
@RestController
@RequestMapping("/api/job-orders")
public class JobOrderController {

    private final JobOrderService jobOrderService;

    public JobOrderController(JobOrderService jobOrderService) {
        this.jobOrderService = jobOrderService;
    }

    /**
     * 列表：三条动态投影查询按参数命中——
     * 日期区间 {@code from/to}、地块 {@code plotId}、状态 {@code status}，形状统一是 JobOrderRow。
     */
    @GetMapping
    public ApiResponse<List<Views.JobOrderView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "plotId", required = false) Long plotId,
            @RequestParam(value = "droneId", required = false) Long droneId,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(jobOrderService.list(status, plotId, droneId, from, to));
    }

    /** 下拉框：同一查询方法取 JobOrderOption 形状 */
    @GetMapping("/options")
    public ApiResponse<List<Views.JobOrderOptionView>> options() {
        return ApiResponse.success(jobOrderService.options());
    }

    @PostMapping
    public ApiResponse<Views.JobOrderView> create(@Valid @RequestBody JobOrderDtos.SaveReq req) {
        return ApiResponse.success("作业单已生成", jobOrderService.create(req));
    }

    /** 待飞作业单改派 / 改口径：任一配药依据变化，原已锁定批次立即失效 */
    @PutMapping("/{id}")
    public ApiResponse<Views.JobOrderView> update(@PathVariable("id") Long id,
                                                  @RequestBody JobOrderDtos.UpdateReq req) {
        return ApiResponse.success("作业单已更新，原配药批次已按变化项失效", jobOrderService.update(id, req));
    }

    /** 待飞 → 作业中 */
    @PostMapping("/{id}/takeoff")
    public ApiResponse<Views.JobOrderView> takeoff(@PathVariable("id") Long id) {
        return ApiResponse.success("已起飞，地块与机身转入作业中", jobOrderService.start(id));
    }

    /** 作业中 → 已完成 */
    @PostMapping("/{id}/finish")
    public ApiResponse<Views.JobOrderView> finish(@PathVariable("id") Long id,
                                                  @RequestBody(required = false) JobOrderDtos.DoneReq req) {
        return ApiResponse.success("作业完成，实际亩数已按飞行日志汇总回填", jobOrderService.done(id, req));
    }

    /** 待飞 / 作业中 → 已取消 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Views.JobOrderView> cancel(@PathVariable("id") Long id,
                                                  @RequestBody(required = false) JobOrderDtos.CancelReq req) {
        return ApiResponse.success("作业单已取消", jobOrderService.cancel(id, req));
    }
}
