package com.drone.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块四（作业单）入参。 */
public final class JobOrderDtos {

    private JobOrderDtos() {
    }

    @Data
    public static class SaveReq {

        @NotBlank(message = "作业单号不能为空")
        private String orderNo;

        @NotNull(message = "地块不能为空")
        private Long plotId;

        @NotNull(message = "无人机不能为空")
        private Long droneId;

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

    @Data
    public static class DoneReq {

        /**
         * 实际作业亩数：服务端不再采信，只用于和已落库飞行日志汇总对账。
         * 不传即按日志汇总完成；传了但与汇总不一致（含 0.01 级误差）一律拒绝并回显两者。
         */
        private BigDecimal actualMu;
    }

    @Data
    public static class CancelReq {

        private String reason;
    }

    /**
     * 待飞作业单改派 / 改口径：字段可空，空表示该项不改。
     * 无人机、药剂配方、亩用量、计划亩数任一变化都会让原已锁定配药批次立即失效。
     */
    @Data
    public static class UpdateReq {

        private Long plotId;

        private Long droneId;

        private Long pilotId;

        private String formula;

        private BigDecimal dosePerMu;

        private BigDecimal plannedMu;

        private LocalDate jobDate;

        private String weather;
    }
}
