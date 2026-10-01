package com.drone.plant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 模块三（飞手）入参。 */
public final class PilotDtos {

    private PilotDtos() {
    }

    @Data
    public static class SaveReq {

        @NotBlank(message = "飞手编号不能为空")
        private String pilotNo;

        @NotBlank(message = "姓名不能为空")
        private String name;

        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号必须是 11 位中国大陆号码")
        private String phone;

        @NotBlank(message = "执照等级不能为空")
        private String licenseLevel;
    }

    @Data
    public static class StatusReq {

        @NotBlank(message = "目标状态不能为空")
        private String status;
    }
}
