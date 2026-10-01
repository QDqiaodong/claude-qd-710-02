package com.drone.plant.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drone.plant.common.ApiResponse;
import com.drone.plant.dto.MixingDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.service.MixingService;

import jakarta.validation.Valid;

/**
 * 模块六：作业前配药复核台。
 *
 * <p>完整入口：按状态查批次、查分桶与复核记录（详情）、从作业单直达批次（by-order）、
 * 发起 / 调桶 / 提交 / 驳回 / 锁定。状态与处理人全部持久化，页面刷新后一致。
 */
@RestController
@RequestMapping("/api/mixing-batches")
public class MixingController {

    private final MixingService mixingService;

    public MixingController(MixingService mixingService) {
        this.mixingService = mixingService;
    }

    /** 按状态查找批次（不传 status 查全部五种状态） */
    @GetMapping
    public ApiResponse<List<Views.MixingBatchView>> list(
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(mixingService.list(status));
    }

    /** 从作业单直接进入对应批次 */
    @GetMapping("/by-order/{jobOrderId}")
    public ApiResponse<List<Views.MixingBatchView>> byOrder(@PathVariable("jobOrderId") Long jobOrderId) {
        return ApiResponse.success(mixingService.listByJobOrder(jobOrderId));
    }

    /** 详情：分桶明细 + 复核时间线 + 快照一致性 */
    @GetMapping("/{id}")
    public ApiResponse<Views.MixingBatchView> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(mixingService.detail(id));
    }

    /** 配药人下拉：全部在队飞手 */
    @GetMapping("/pilot-options")
    public ApiResponse<List<Views.PilotOptionView>> pilotOptions() {
        return ApiResponse.success(mixingService.activePilotOptions());
    }

    /** 复核人下拉：在队且三级 / 教员（是否为“另一名”由提交时服务端判定） */
    @GetMapping("/reviewer-options")
    public ApiResponse<List<Views.PilotOptionView>> reviewerOptions() {
        return ApiResponse.success(mixingService.reviewerOptions());
    }

    /** 从待飞作业单发起配药批次（不传 buckets 则自动出最少桶数方案） */
    @PostMapping
    public ApiResponse<Views.MixingBatchView> create(@Valid @RequestBody MixingDtos.CreateReq req) {
        return ApiResponse.success("配药批次已发起，默认按最少桶数分桶", mixingService.create(req));
    }

    /** 人工调整各桶药量 */
    @PutMapping("/{id}/buckets")
    public ApiResponse<Views.MixingBatchView> updateBuckets(@PathVariable("id") Long id,
                                                            @Valid @RequestBody MixingDtos.BucketsReq req) {
        return ApiResponse.success("分桶已更新，合计与载荷校验通过", mixingService.updateBuckets(id, req));
    }

    /** 提交复核（提交前重新校验分桶） */
    @PostMapping("/{id}/submit")
    public ApiResponse<Views.MixingBatchView> submit(@PathVariable("id") Long id,
                                                     @Valid @RequestBody MixingDtos.ActorReq req) {
        return ApiResponse.success("已提交复核，等待另一名三级/教员飞手确认", mixingService.submit(id, req));
    }

    /** 驳回（原因必填） */
    @PostMapping("/{id}/reject")
    public ApiResponse<Views.MixingBatchView> reject(@PathVariable("id") Long id,
                                                     @Valid @RequestBody MixingDtos.RejectReq req) {
        return ApiResponse.success("已驳回，配药人可修改分桶后再次提交", mixingService.reject(id, req));
    }

    /** 复核锁定：双人同时确认时只有一人成功，另一人收到“已被处理” */
    @PostMapping("/{id}/lock")
    public ApiResponse<Views.MixingBatchView> lock(@PathVariable("id") Long id,
                                                   @Valid @RequestBody MixingDtos.LockReq req) {
        return ApiResponse.success("复核通过，批次已锁定，作业单可以起飞", mixingService.lock(id, req));
    }
}
