package com.drone.plant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.drone.plant.common.GlobalExceptionHandler;
import com.drone.plant.dto.JobOrderDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.service.JobOrderService;

/**
 * 完成接口验证：请求体携带的 actualMu 只做对账，不一致 / 汇总为零时 400 且回显两个数值；
 * 正常完成返回 200 且实际亩数等于日志汇总。
 */
@WebMvcTest(JobOrderController.class)
@Import(GlobalExceptionHandler.class)
class JobOrderFinishControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JobOrderService jobOrderService;

    @Test
    void rejectsMismatchedActualMuWithBothValuesInMessage() throws Exception {
        when(jobOrderService.done(eq(7L), any(JobOrderDtos.DoneReq.class)))
                .thenThrow(new com.drone.plant.common.BizException(
                        "实际作业亩数只能取该作业单已落库飞行日志的汇总亩数，不能手工填写："
                                + "提交的是 80.00 亩，飞行日志汇总是 52.00 亩，两者不一致，已拒绝完成"));

        mvc.perform(post("/api/job-orders/7/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualMu\": 80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("提交的是 80.00 亩")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("飞行日志汇总是 52.00 亩")));
    }

    @Test
    void rejectsZeroLogSummary() throws Exception {
        when(jobOrderService.done(eq(7L), any()))
                .thenThrow(new com.drone.plant.common.BizException(
                        "作业单 JO-TEST01 还没有登记有效的飞行日志（汇总亩数为 0），不能完成；请先在飞行日志页登记架次"));

        mvc.perform(post("/api/job-orders/7/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("汇总亩数为 0")));
    }

    @Test
    void finishesWithLoggedSummaryWhenBodyOmitsActualMu() throws Exception {
        when(jobOrderService.done(eq(7L), any())).thenReturn(doneView("52.00", "52.00"));

        mvc.perform(post("/api/job-orders/7/finish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.actualMu").value(52.00))
                .andExpect(jsonPath("$.data.loggedMu").value(52.00));

        verify(jobOrderService).done(eq(7L), any(JobOrderDtos.DoneReq.class));
    }

    private Views.JobOrderView doneView(String actual, String logged) {
        return new Views.JobOrderView(7L, "JO-TEST01", 10L, "P-10", "测试农户", 20L, "DP-20", "测试机型",
                "IDLE", "在库", false, 30L, "测试飞手", "test formula", new BigDecimal("1.20"),
                new BigDecimal("100.00"), new BigDecimal(actual), new BigDecimal(logged),
                LocalDate.of(2026, 9, 28), "晴", "DONE", "已完成", 52, 2, new BigDecimal("62.40"),
                LocalDateTime.of(2026, 9, 28, 12, 0), null, null, null, null);
    }
}
