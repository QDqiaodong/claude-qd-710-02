package com.drone.plant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块五（飞行日志）入参。 */
public final class FlightLogDtos {

    private FlightLogDtos() {
    }

    @Data
    public static class SaveReq {

        @NotNull(message = "作业单不能为空")
        private Long jobOrderId;

        @NotNull(message = "起飞时间不能为空")
        private LocalDateTime takeoffAt;

        @NotNull(message = "降落时间不能为空")
        private LocalDateTime landingAt;

        @NotNull(message = "本架次作业亩数不能为空")
        private BigDecimal areaMu;

        @NotNull(message = "消耗药量不能为空")
        private BigDecimal chemicalL;

        /** 风速（m/s），可空：不传按无风处理 */
        private BigDecimal windSpeed;

        /** 气温，可空 */
        private BigDecimal temperature;

        /** 是否异常，可空：不传按风速阈值自动判定 */
        private Boolean abnormal;

        private String abnormalDesc;
    }
}
