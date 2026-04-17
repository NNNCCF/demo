package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.domain.AlarmHandleStatus;
import com.ncf.demo.entity.Alarm;
import com.ncf.demo.entity.Ward;
import com.ncf.demo.repository.AlarmRepository;
import com.ncf.demo.repository.WardRepository;
import com.ncf.demo.web.dto.AlarmHandleRequest;
import com.ncf.demo.web.dto.AlarmReportRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AlarmManagementService {
    private final AlarmRepository alarmRepository;
    private final WardRepository wardRepository;

    public AlarmManagementService(AlarmRepository alarmRepository, WardRepository wardRepository) {
        this.alarmRepository = alarmRepository;
        this.wardRepository = wardRepository;
    }

    public List<Alarm> query(Long guardianId, Instant start, Instant end) {
        if (guardianId != null) {
            List<Ward> wards = wardRepository.findByDeviceGuardianId(guardianId);
            if (!wards.isEmpty()) {
                List<Long> targetIds = wards.stream().map(Ward::getMemberId).toList();
                return alarmRepository.findByTargetIdInAndOccurredAtBetween(targetIds, start, end).stream()
                        .sorted(Comparator.comparing(Alarm::getOccurredAt).reversed())
                        .toList();
            }
        }
        Long orgId = com.ncf.demo.security.SecurityUtil.currentOrgId();
        if (orgId != null) {
            List<Long> targetIds = wardRepository.findByDeviceFamilyOrgId(orgId).stream()
                    .map(Ward::getMemberId).toList();
            if (targetIds.isEmpty()) return List.of();
            return alarmRepository.findByTargetIdInAndOccurredAtBetween(targetIds, start, end).stream()
                    .sorted(Comparator.comparing(Alarm::getOccurredAt).reversed())
                    .toList();
        }
        return alarmRepository.findByOccurredAtBetween(start, end).stream()
                .sorted(Comparator.comparing(Alarm::getOccurredAt).reversed())
                .toList();
    }

    public List<Alarm> queryByGuardian(Long guardianId, Instant start, Instant end) {
        return query(guardianId, start, end);
    }

    @Transactional
    public void handle(Long id, AlarmHandleRequest request) {
        Alarm alarm = alarmRepository.findById(id).orElseThrow(() -> new BizException(404, "告警不存在"));
        if (request.handleStatus() == AlarmHandleStatus.UNHANDLED) {
            throw new BizException(400, "处理状态不合法");
        }
        alarm.setHandleStatus(request.handleStatus());
        alarm.setHandledBy(request.handlerId());
        alarm.setHandledAt(Instant.now());
        alarm.setHandleRemark(request.handleRemark());
        alarmRepository.save(alarm);
    }

    @Transactional
    public int handleAll(Long handlerId) {
        return alarmRepository.handleAllUnhandled(handlerId, Instant.now(), "一键处理");
    }

    @Transactional
    public void clearAll() {
        alarmRepository.deleteAll();
    }

    public byte[] exportReport(Instant start, Instant end) {
        List<Alarm> alarms = alarmRepository.findAll().stream()
                .filter(alarm -> !alarm.getOccurredAt().isBefore(start) && !alarm.getOccurredAt().isAfter(end))
                .toList();
        Map<String, List<Alarm>> grouped = alarms.stream().collect(Collectors.groupingBy(a -> a.getAlarmType().name()));
        List<AlarmReportRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<Alarm>> entry : grouped.entrySet()) {
            long handled = entry.getValue().stream().filter(a -> a.getHandleStatus() != AlarmHandleStatus.UNHANDLED).count();
            String handleRate = entry.getValue().isEmpty() ? "0.00%" : String.format("%.2f%%", handled * 100D / entry.getValue().size());
            String source = entry.getValue().stream().collect(Collectors.groupingBy(Alarm::getDeviceId, Collectors.counting()))
                    .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-");
            rows.add(new AlarmReportRow(entry.getKey(), (long) entry.getValue().size(), handleRate, source));
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("alarm-report");
            Row head = sheet.createRow(0);
            head.createCell(0).setCellValue("告警类型");
            head.createCell(1).setCellValue("数量");
            head.createCell(2).setCellValue("处理率");
            head.createCell(3).setCellValue("主要告警来源");
            for (int i = 0; i < rows.size(); i++) {
                AlarmReportRow row = rows.get(i);
                Row excelRow = sheet.createRow(i + 1);
                excelRow.createCell(0).setCellValue(row.alarmType());
                excelRow.createCell(1).setCellValue(row.count());
                excelRow.createCell(2).setCellValue(row.handleRate());
                excelRow.createCell(3).setCellValue(row.majorSource());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(500, "导出失败");
        }
    }
}
