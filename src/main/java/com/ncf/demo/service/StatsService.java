package com.ncf.demo.service;

import com.ncf.demo.entity.Alarm;
import com.ncf.demo.entity.Ward;
import com.ncf.demo.repository.AlarmRepository;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.repository.WardRepository;
import com.ncf.demo.web.dto.CommunityStatResponse;
import com.ncf.demo.web.dto.HeartRateTrendPoint;
import com.ncf.demo.web.dto.MonthlyDeviceStat;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {
    private final DeviceRepository deviceRepository;
    private final WardRepository wardRepository;
    private final AlarmRepository alarmRepository;
    private final TdengineService tdengineService;

    public StatsService(
            DeviceRepository deviceRepository,
            WardRepository wardRepository,
            AlarmRepository alarmRepository,
            TdengineService tdengineService
    ) {
        this.deviceRepository = deviceRepository;
        this.wardRepository = wardRepository;
        this.alarmRepository = alarmRepository;
        this.tdengineService = tdengineService;
    }

    /** GET /stats/monthly — 近12个月每月活跃设备数与告警数 */
    public List<MonthlyDeviceStat> monthlyStats() {
        Instant end = Instant.now();
        Instant start = end.minusSeconds(365L * 24 * 3600);
        List<Alarm> alarms = alarmRepository.findByOccurredAtBetween(start, end);

        Map<String, Long> alarmByMonth = alarms.stream().collect(Collectors.groupingBy(
                a -> YearMonth.from(a.getOccurredAt().atZone(ZoneId.systemDefault())).toString(),
                Collectors.counting()
        ));

        long totalDevices = deviceRepository.count();
        List<MonthlyDeviceStat> result = new ArrayList<>();
        YearMonth cursor = YearMonth.now().minusMonths(11);
        for (int i = 0; i < 12; i++) {
            String month = cursor.toString();
            result.add(new MonthlyDeviceStat(month, totalDevices, alarmByMonth.getOrDefault(month, 0L)));
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    /** GET /stats/trend — 指定设备心率趋势（按小时聚合） */
    public List<HeartRateTrendPoint> trend(String deviceId, int days) {
        Instant end = Instant.now();
        Instant start = end.minusSeconds((long) days * 24 * 3600);
        List<Map<String, Object>> rows = tdengineService.queryDeviceData("heart_rate", deviceId, start, end);

        Map<String, List<Integer>> bucket = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Instant ts = ((java.sql.Timestamp) row.get("ts")).toInstant();
            String hour = ts.atZone(ZoneId.systemDefault())
                    .toLocalDateTime().withMinute(0).withSecond(0).withNano(0).toString();
            int rate = Integer.parseInt(String.valueOf(row.get("heart_rate")));
            bucket.computeIfAbsent(hour, k -> new ArrayList<>()).add(rate);
        }

        return bucket.entrySet().stream()
                .map(e -> {
                    List<Integer> vals = e.getValue();
                    double avg = vals.stream().mapToInt(Integer::intValue).average().orElse(0);
                    double max = vals.stream().mapToInt(Integer::intValue).max().orElse(0);
                    double min = vals.stream().mapToInt(Integer::intValue).min().orElse(0);
                    return new HeartRateTrendPoint(e.getKey(), avg, max, min);
                })
                .collect(Collectors.toList());
    }

    /** GET /stats/community — 按机构统计设备数与告警数 */
    public List<CommunityStatResponse> communityStats() {
        List<Ward> allWards = wardRepository.findAll();
        // Group wards by their device's family's org
        Map<Long, List<Ward>> byOrg = allWards.stream()
                .filter(w -> w.getDevice() != null
                        && w.getDevice().getFamilyId() != null)
                .collect(Collectors.groupingBy(w -> w.getDevice().getFamilyId()));

        Instant end = Instant.now();
        Instant start = end.minusSeconds(30L * 24 * 3600);

        List<CommunityStatResponse> result = new ArrayList<>();
        for (Map.Entry<Long, List<Ward>> entry : byOrg.entrySet()) {
            Long familyId = entry.getKey();
            List<Long> targetIds = entry.getValue().stream().map(Ward::getMemberId).toList();
            long deviceCount = entry.getValue().stream()
                    .map(Ward::getDevice)
                    .distinct().count();
            long alarmCount = alarmRepository.findByTargetIdInAndOccurredAtBetween(targetIds, start, end).size();
            result.add(new CommunityStatResponse(familyId, "家庭" + familyId, deviceCount, alarmCount));
        }
        return result;
    }
}
