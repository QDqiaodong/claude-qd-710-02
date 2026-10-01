package com.drone.plant.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块六（作业前配药复核台）入参。 */
public final class MixingDtos {

    private MixingDtos() {
    }

    /** 从待飞作业单发起配药批次 */
    @Data
    public static class CreateReq {

        @NotNull(message = "作业单不能为空")
        private Long jobOrderId;

        /** 配药人（必须是在队飞手） */
        @NotNull(message = "配药人不能为空")
        private Long preparedPilotId;

        /**
         * 分桶药量（L），可空：不传则按“桶数最少 + 不超载”自动出默认方案。
         * 传了就按人工口径校验：每桶 > 0、每桶 ≤ 载荷快照、合计与总药量精确一致。
         */
        private List<BigDecimal> buckets;
    }

    /** 人工调整各桶药量 */
    @Data
    public static class BucketsReq {

        @NotNull(message = "请先选择当前操作飞手")
        private Long pilotId;

        @NotEmpty(message = "至少保留一个桶")
        private List<BigDecimal> amounts;
    }

    /** 提交 / 驳回 / 锁定都要带当前操作飞手（复核台右上角的在岗身份） */
    @Data
    public static class ActorReq {

        @NotNull(message = "请先选择当前操作飞手")
        private Long pilotId;
    }

    /** 驳回：原因必填 */
    @Data
    public static class RejectReq {

        @NotNull(message = "请先选择当前复核飞手")
        private Long pilotId;

        private String reason;
    }

    /** 锁定确认（双人并发抢锁时，后到者收到“已被处理”） */
    @Data
    public static class LockReq {

        @NotNull(message = "请先选择当前复核飞手")
        private Long pilotId;
    }
}
