package com.ncf.demo.web;

import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system/status")
public class SystemStatusController {

    private final HealthEndpoint healthEndpoint;
    private final MetricsEndpoint metricsEndpoint;

    public SystemStatusController(HealthEndpoint healthEndpoint, MetricsEndpoint metricsEndpoint) {
        this.healthEndpoint = healthEndpoint;
        this.metricsEndpoint = metricsEndpoint;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();

        // --- 各服务健康状态 ---
        Map<String, Object> services = new LinkedHashMap<>();
        var health = healthEndpoint.health();
        if (health instanceof CompositeHealth composite && composite.getComponents() != null) {
            composite.getComponents().forEach((name, component) -> {
                Map<String, Object> svc = new LinkedHashMap<>();
                svc.put("status", component.getStatus().getCode());
                services.put(name, svc);
            });
        }
        result.put("services", services);

        // --- JVM 内存 ---
        long heapUsed = longMetric("jvm.memory.used", List.of("area:heap"));
        long heapMax  = longMetric("jvm.memory.max",  List.of("area:heap"));
        long heapUsedMB = heapUsed / (1024 * 1024);
        long heapMaxMB  = heapMax  / (1024 * 1024);
        double heapPct  = heapMax > 0 ? (heapUsed * 100.0 / heapMax) : 0;
        long liveThreads = longMetric("jvm.threads.live", List.of());

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMB",  heapUsedMB);
        jvm.put("heapMaxMB",   heapMaxMB);
        jvm.put("heapUsedPct", Math.round(heapPct * 10.0) / 10.0);
        jvm.put("liveThreads", liveThreads);
        result.put("jvm", jvm);

        // --- 系统资源 ---
        double cpuUsage   = doubleMetric("system.cpu.usage", List.of());
        long diskFree  = longMetric("disk.free",  List.of());
        long diskTotal = longMetric("disk.total", List.of());
        long diskFreeMB  = diskFree  / (1024 * 1024);
        long diskTotalMB = diskTotal / (1024 * 1024);
        double diskUsedPct = diskTotal > 0 ? ((diskTotal - diskFree) * 100.0 / diskTotal) : 0;

        Map<String, Object> system = new LinkedHashMap<>();
        system.put("cpuUsagePct",  Math.round(cpuUsage * 1000.0) / 10.0);
        system.put("diskFreeMB",   diskFreeMB);
        system.put("diskTotalMB",  diskTotalMB);
        system.put("diskUsedPct",  Math.round(diskUsedPct * 10.0) / 10.0);
        result.put("system", system);

        // --- 运行时信息 ---
        double uptimeSeconds = doubleMetric("process.uptime", List.of());
        double startEpoch    = doubleMetric("process.start.time", List.of());

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("uptimeSeconds", (long) uptimeSeconds);
        runtime.put("startTime",     startEpoch > 0 ? Instant.ofEpochMilli((long)(startEpoch * 1000)).toString() : null);
        runtime.put("javaVersion",   System.getProperty("java.version"));
        result.put("runtime", runtime);

        return ApiResponse.ok(result);
    }

    private double doubleMetric(String name, List<String> tags) {
        try {
            var metric = metricsEndpoint.metric(name, tags);
            if (metric == null) return 0;
            return metric.getMeasurements().stream()
                    .findFirst()
                    .map(m -> m.getValue())
                    .orElse(0.0);
        } catch (Exception e) {
            return 0;
        }
    }

    private long longMetric(String name, List<String> tags) {
        return (long) doubleMetric(name, tags);
    }
}
