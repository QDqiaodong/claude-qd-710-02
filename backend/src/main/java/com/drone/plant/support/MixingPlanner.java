package com.drone.plant.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认分桶算法：在“桶数最少”与“任一桶不超载”之间取一致口径。
 *
 * <p>取舍原则（需求的一致校验口径）：
 * <ol>
 *   <li>桶数最少：n = ceil(总药量 / 单桶最大载荷)；</li>
 *   <li>任一桶不超载：基数 = floor(总量 / n / 0.01) × 0.01，余量以 0.01L 为颗粒
 *       从第 1 桶起依次 +0.01 均摊，而不是把误差全部塞进最后一桶；</li>
 *   <li>因此最大的桶是第 1 桶（base+0.01），数学上必然 ≤ ceil(总量/n) ≤ 载荷，
 *       每桶 > 0，合计与计划总药量分毫不差。</li>
 * </ol>
 * 人工调整后服务端 {@code validateBuckets} 用同一口径复核：每桶>0、每桶≤载荷、合计精确守恒。
 */
public final class MixingPlanner {

    private MixingPlanner() {
    }

    /** 分为最小桶数的方案，返回值 2 位小数、非空、守恒且不超载。 */
    public static List<BigDecimal> plan(BigDecimal totalL, BigDecimal maxPayload) {
        BigDecimal total = scaled(totalL);
        BigDecimal payload = scaled(maxPayload);
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("总药量必须大于 0");
        }
        if (payload.signum() <= 0) {
            throw new IllegalArgumentException("最大载荷必须大于 0");
        }
        // 桶数：ceil(总药量 / 载荷)
        int buckets = total.divide(payload, 0, RoundingMode.CEILING).intValueExact();

        long totalCents = total.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        long baseCents = totalCents / buckets;
        long remainder = totalCents % buckets;

        List<BigDecimal> result = new ArrayList<>(buckets);
        for (int i = 0; i < buckets; i++) {
            long cents = baseCents + (i < remainder ? 1 : 0);
            result.add(BigDecimal.valueOf(cents, 2));
        }
        return result;
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
