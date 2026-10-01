package com.drone.plant.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块一（地块）入参。 */
public final class PlotDtos {

    private PlotDtos() {
    }

    @Data
    public static class SaveReq {

        @NotBlank(message = "地块编号不能为空")
        private String plotNo;

        @NotBlank(message = "承包农户 / 合作社不能为空")
        private String farmerName;

        @NotBlank(message = "作物类型不能为空")
        private String cropType;

        @NotNull(message = "面积不能为空")
        private BigDecimal areaMu;

        @NotBlank(message = "所属村组不能为空")
        private String villageGroup;

        @NotBlank(message = "地形不能为空")
        private String terrain;

        @NotBlank(message = "灌溉条件不能为空")
        private String irrigation;
    }

    @Data
    public static class StatusReq {

        @NotBlank(message = "目标状态不能为空")
        private String status;
    }
}
