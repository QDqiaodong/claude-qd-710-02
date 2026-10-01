package com.drone.plant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块五-延（异常架次停飞处置）入参。处置由异常架次登记时自动立案，这里只有主管的三种处置动作。 */
public final class HandlingDtos {

    private HandlingDtos() {
    }

    /** 继续观察 / 停飞机身 / 解除的共同入参：值班主管 + 处置意见（解除时为处置结论） */
    @Data
    public static class ActReq {

        @NotNull(message = "请先选择当前值班主管")
        private Long supervisorId;

        /** 继续观察意见 / 停飞意见 / 解除结论（解除必填，由服务端校验） */
        private String comment;
    }
}
