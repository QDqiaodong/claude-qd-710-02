package com.drone.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块二（植保无人机）入参。 */
public final class DroneDtos {

    private DroneDtos() {
    }

    @Data
    public static class SaveReq {

        @NotBlank(message = "机身编号不能为空")
        private String bodyNo;

        @NotBlank(message = "型号不能为空")
        private String model;

        @NotBlank(message = "类型不能为空")
        private String droneType;

        @NotNull(message = "最大载荷不能为空")
        private BigDecimal maxPayload;

        @NotNull(message = "喷幅不能为空")
        private BigDecimal sprayWidth;

        @NotNull(message = "电池组数不能为空")
        private Integer batteryPacks;

        private LocalDate purchaseDate;
    }

    @Data
    public static class StatusReq {

        @NotBlank(message = "目标状态不能为空")
        private String status;
    }

    /** 派机：给这台机配一块地、一个飞手，直接生成待飞作业单 */
    @Data
    public static class DispatchReq {

        @NotBlank(message = "作业单号不能为空")
        private String orderNo;

        @NotNull(message = "地块不能为空")
        private Long plotId;

        @NotNull(message = "飞手不能为空")
        private Long pilotId;

        @NotBlank(message = "药剂配方不能为空")
        private String formula;

        @NotNull(message = "亩用量不能为空")
        private BigDecimal dosePerMu;

        @NotNull(message = "计划作业亩数不能为空")
        private BigDecimal plannedMu;

        @NotNull(message = "作业日期不能为空")
        private LocalDate jobDate;

        private String weather;
    }
}
