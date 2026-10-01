package com.drone.plant.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drone.plant.common.BizException;
import com.drone.plant.dto.PilotDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.Pilot;
import com.drone.plant.projection.JobOrderBrief;
import com.drone.plant.projection.PilotCard;
import com.drone.plant.projection.PilotOption;
import com.drone.plant.projection.PlotOption;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;
import com.drone.plant.support.PlantRules;

/**
 * 模块三：飞手。
 *
 * <p>{@link PilotCard}（卡片墙）与 {@link PilotOption}（派工下拉框）共用同一批动态投影查询方法。
 */
@Service
public class PilotService {

    private final PilotRepository pilotRepository;
    private final JobOrderRepository jobOrderRepository;
    private final PlotRepository plotRepository;

    public PilotService(PilotRepository pilotRepository, JobOrderRepository jobOrderRepository,
                        PlotRepository plotRepository) {
        this.pilotRepository = pilotRepository;
        this.jobOrderRepository = jobOrderRepository;
        this.plotRepository = plotRepository;
    }

    @Transactional(readOnly = true)
    public List<Views.PilotView> list(String status, String licenseLevel) {
        List<PilotCard> rows;
        if (isBlank(status)) {
            rows = isBlank(licenseLevel)
                    ? pilotRepository.findAllByOrderByPilotNoAsc(PilotCard.class)
                    : pilotRepository.findByLicenseLevelOrderByTotalSortsDesc(licenseLevel, PilotCard.class);
        } else {
            rows = pilotRepository.findByStatusOrderByPilotNoAsc(status, PilotCard.class);
        }
        Map<Long, PilotStat> stats = pilotStats();
        List<Views.PilotView> list = new ArrayList<>();
        for (PilotCard row : rows) {
            PilotStat stat = stats.get(row.getId());
            list.add(new Views.PilotView(row.getId(), row.getPilotNo(), row.getName(), row.getPhone(),
                    row.getLicenseLevel(), PlantRules.licenseName(row.getLicenseLevel()),
                    PlantRules.licenseColor(row.getLicenseLevel()), row.getTotalSorts(), row.getTotalAreaMu(),
                    row.getStatus(), PlantRules.pilotStatusName(row.getStatus()),
                    stat == null ? 0 : stat.openOrders, stat == null ? null : stat.currentPlotNo, null));
        }
        return list;
    }

    /** 下拉框：同一方法取 {@link PilotOption} 形状 */
    @Transactional(readOnly = true)
    public List<Views.PilotOptionView> options() {
        List<Views.PilotOptionView> list = new ArrayList<>();
        for (PilotOption row : pilotRepository.findAllByOrderByPilotNoAsc(PilotOption.class)) {
            list.add(new Views.PilotOptionView(row.getId(), row.getPilotNo(), row.getName(),
                    PlantRules.licenseName(row.getLicenseLevel()), row.getStatus()));
        }
        return list;
    }

    @Transactional
    public Views.PilotView create(PilotDtos.SaveReq req) {
        String pilotNo = trim(req.getPilotNo());
        if (pilotNo.isEmpty()) {
            throw new BizException("飞手编号不能为空");
        }
        if (pilotRepository.existsByPilotNo(pilotNo)) {
            throw new BizException("飞手编号 " + pilotNo + " 已存在");
        }
        String licenseLevel = upper(req.getLicenseLevel());
        if (!PlantRules.isValidLicenseLevel(licenseLevel)) {
            throw new BizException("执照等级只能是 LEVEL1 / LEVEL2 / LEVEL3 / INSTRUCTOR，收到的是：" + req.getLicenseLevel());
        }
        String name = trim(req.getName());
        if (name.isEmpty()) {
            throw new BizException("飞手姓名不能为空");
        }
        String phone = trim(req.getPhone());
        if (!phone.matches("1[3-9]\\d{9}")) {
            throw new BizException("手机号必须是 11 位中国大陆号码，收到的是：" + phone);
        }
        if (pilotRepository.existsByPhone(phone)) {
            throw new BizException("手机号 " + phone + " 已被其它飞手登记");
        }
        Pilot pilot = new Pilot();
        pilot.setPilotNo(pilotNo);
        pilot.setName(name);
        pilot.setPhone(phone);
        pilot.setLicenseLevel(licenseLevel);
        pilot.setTotalSorts(0);
        pilot.setTotalAreaMu(BigDecimal.ZERO);
        pilot.setStatus("ACTIVE");
        pilot.setUpdatedAt(LocalDateTime.now());
        Pilot saved = pilotRepository.save(pilot);
        return new Views.PilotView(saved.getId(), saved.getPilotNo(), saved.getName(), saved.getPhone(),
                saved.getLicenseLevel(), PlantRules.licenseName(saved.getLicenseLevel()),
                PlantRules.licenseColor(saved.getLicenseLevel()), saved.getTotalSorts(), saved.getTotalAreaMu(),
                saved.getStatus(), PlantRules.pilotStatusName(saved.getStatus()), 0, null, saved.getUpdatedAt());
    }

    @Transactional
    public Views.PilotView updateStatus(Long id, String status) {
        Pilot pilot = pilotRepository.findById(id)
                .orElseThrow(() -> new BizException("飞手不存在：id=" + id));
        String target = upper(status);
        if (!PlantRules.isValidPilotStatus(target)) {
            throw new BizException("飞手状态只能是 ACTIVE / LEAVE / RESIGNED，收到的是：" + status);
        }
        for (JobOrderBrief order : jobOrderRepository.findAllByOrderByJobDateDesc(JobOrderBrief.class)) {
            if (id.equals(order.getPilotId())
                    && ("PENDING".equals(order.getStatus()) || "WORKING".equals(order.getStatus()))) {
                throw new BizException(pilot.getName() + " 还有在手作业单 " + order.getOrderNo()
                        + "，先把作业单收尾再改状态");
            }
        }
        pilot.setStatus(target);
        pilot.setUpdatedAt(LocalDateTime.now());
        Pilot saved = pilotRepository.save(pilot);
        PilotStat stat = pilotStats().get(id);
        return new Views.PilotView(saved.getId(), saved.getPilotNo(), saved.getName(), saved.getPhone(),
                saved.getLicenseLevel(), PlantRules.licenseName(saved.getLicenseLevel()),
                PlantRules.licenseColor(saved.getLicenseLevel()), saved.getTotalSorts(), saved.getTotalAreaMu(),
                saved.getStatus(), PlantRules.pilotStatusName(saved.getStatus()),
                stat == null ? 0 : stat.openOrders, stat == null ? null : stat.currentPlotNo,
                saved.getUpdatedAt());
    }

    private Map<Long, PilotStat> pilotStats() {
        Map<Long, String> plotNoMap = new HashMap<>();
        for (PlotOption row : plotRepository.findAllByOrderByPlotNoAsc(PlotOption.class)) {
            plotNoMap.put(row.getId(), row.getPlotNo());
        }
        Map<Long, PilotStat> map = new HashMap<>();
        for (JobOrderBrief order : jobOrderRepository.findAllByOrderByJobDateDesc(JobOrderBrief.class)) {
            if (order.getPilotId() == null) {
                continue;
            }
            PilotStat old = map.getOrDefault(order.getPilotId(), new PilotStat(0, null));
            long open = old.openOrders
                    + ("PENDING".equals(order.getStatus()) || "WORKING".equals(order.getStatus()) ? 1 : 0);
            String plotNo = "WORKING".equals(order.getStatus()) ? plotNoMap.get(order.getPlotId()) : old.currentPlotNo;
            map.put(order.getPilotId(), new PilotStat(open, plotNo));
        }
        return map;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record PilotStat(long openOrders, String currentPlotNo) {
    }
}
