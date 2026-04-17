package com.ncf.demo.web.dto;

import java.util.List;
import java.util.Map;

public record DataHistoryResponse(
        List<Map<String, Object>> dataList,
        int total,
        long avgSampleIntervalSeconds
) {
}
