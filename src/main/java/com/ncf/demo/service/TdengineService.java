package com.ncf.demo.service;

import com.ncf.demo.config.AppProperties;
import com.ncf.demo.domain.DeviceType;
import com.ncf.demo.service.model.ParsedDeviceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Service
public class TdengineService {
    private static final Logger log = LoggerFactory.getLogger(TdengineService.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private final AppProperties appProperties;
    private final SystemConfigService systemConfigService;
    private final AtomicInteger failCount = new AtomicInteger(0);
    private volatile long openUntil = 0L;

    /**
     * 将 deviceId 转为合法的 TDengine 表名后缀。
     * TDengine 表名不允许连字符，将 '-' 替换为 '_' 并转小写。
     * 例：DEV-CF-001 → dev_cf_001，原始 deviceId 仍通过 TAGS 参数正确存储。
     */
    private static String toTableSuffix(String deviceId) {
        return deviceId.replace("-", "_").toLowerCase();
    }

    /** 校验原始 deviceId，防止 SQL 注入（用于 TAGS 参数，不用于表名拼接）。*/
    private static void validateIdentifier(String value, String fieldName) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("非法的 " + fieldName + " 格式: " + value);
        }
    }

    public TdengineService(AppProperties appProperties, SystemConfigService systemConfigService) {
        this.appProperties = appProperties;
        this.systemConfigService = systemConfigService;
    }

    static DatabaseTarget parseDatabaseTarget(String fullUrl) {
        if (fullUrl == null || fullUrl.isBlank()) {
            throw new IllegalArgumentException("TDengine URL must not be blank");
        }

        int schemeSeparator = fullUrl.indexOf("://");
        if (schemeSeparator < 0) {
            throw new IllegalArgumentException("Unsupported TDengine URL: " + fullUrl);
        }

        int databaseSeparator = fullUrl.indexOf('/', schemeSeparator + 3);
        if (databaseSeparator < 0 || databaseSeparator == fullUrl.length() - 1) {
            throw new IllegalArgumentException("Missing database name in TDengine URL: " + fullUrl);
        }

        int querySeparator = fullUrl.indexOf('?', databaseSeparator + 1);
        String databaseName = querySeparator >= 0
                ? fullUrl.substring(databaseSeparator + 1, querySeparator)
                : fullUrl.substring(databaseSeparator + 1);
        if (databaseName.isBlank() || databaseName.contains("/")) {
            throw new IllegalArgumentException("Invalid database name in TDengine URL: " + fullUrl);
        }

        String baseUrl = fullUrl.substring(0, databaseSeparator + 1);
        if (querySeparator >= 0) {
            baseUrl += fullUrl.substring(querySeparator);
        }

        return new DatabaseTarget(baseUrl, databaseName);
    }

    public void initTables() {
        initDatabase();
        execute("""
                CREATE STABLE IF NOT EXISTS heart_rate (ts TIMESTAMP, heart_rate INT, battery INT) TAGS (device_id NCHAR(64))
                """);
        execute("""
                CREATE STABLE IF NOT EXISTS fall_status (ts TIMESTAMP, fall_state INT, battery INT) TAGS (device_id NCHAR(64))
                """);
        execute("""
                CREATE STABLE IF NOT EXISTS location (ts TIMESTAMP, lat DOUBLE, lng DOUBLE, speed DOUBLE) TAGS (device_id NCHAR(64))
                """);
        execute("""
                CREATE STABLE IF NOT EXISTS health_monitor (ts TIMESTAMP, heart_rate INT, breath_rate INT, is_fall BOOL, is_person BOOL) TAGS (device_id NCHAR(64))
                """);
        execute("""
                CREATE STABLE IF NOT EXISTS device_log (ts TIMESTAMP, log_type NCHAR(64), content NCHAR(4096)) TAGS (device_id NCHAR(64))
                """);
    }

    private void initDatabase() {
        String fullUrl = appProperties.getTdengine().getUrl();
        try {
            DatabaseTarget databaseTarget = parseDatabaseTarget(fullUrl);
            try (Connection connection = DriverManager.getConnection(
                    databaseTarget.baseUrl(),
                    appProperties.getTdengine().getUsername(),
                    appProperties.getTdengine().getPassword()
            ); PreparedStatement preparedStatement = connection.prepareStatement(
                    "CREATE DATABASE IF NOT EXISTS " + databaseTarget.databaseName()
            )) {
                preparedStatement.execute();
                log.info("Database {} ensured.", databaseTarget.databaseName());
            }
        } catch (Exception e) {
            log.warn("Failed to init database, it might already exist or connection failed: {}", e.getMessage());
        }
    }

    record DatabaseTarget(String baseUrl, String databaseName) {
    }

    public void save(ParsedDeviceData data) {
        if (System.currentTimeMillis() < openUntil) {
            return;
        }
        validateIdentifier(data.deviceId(), "deviceId");
        String tbl = toTableSuffix(data.deviceId());
        if (data.deviceType() == DeviceType.HEART_RATE) {
            execute("INSERT INTO heart_rate_" + tbl + " USING heart_rate TAGS (?) VALUES (?, ?, ?)",
                    data.deviceId(), Timestamp.from(data.collectTime()), data.payload().get("heartRate"), data.payload().get("battery"));
        } else if (data.deviceType() == DeviceType.FALL_DETECTOR) {
            execute("INSERT INTO fall_status_" + tbl + " USING fall_status TAGS (?) VALUES (?, ?, ?)",
                    data.deviceId(), Timestamp.from(data.collectTime()), data.payload().get("fallState"), data.payload().getOrDefault("battery", 0));
            Object heartRate = data.payload().get("heart_rate_per_min");
            Object breathRate = data.payload().get("breath_rate_per_min");
            Object isFall = data.payload().get("is_fall");
            Object isPerson = data.payload().get("is_person_present");
            if (heartRate != null || breathRate != null) {
                execute("INSERT INTO health_monitor_" + tbl + " USING health_monitor TAGS (?) VALUES (?, ?, ?, ?, ?)",
                        data.deviceId(),
                        Timestamp.from(data.collectTime()),
                        heartRate != null ? heartRate : 0,
                        breathRate != null ? breathRate : 0,
                        isFall != null ? isFall : false,
                        isPerson != null ? isPerson : false
                );
            }
        } else if (data.deviceType() == DeviceType.LOCATOR) {
            execute("INSERT INTO location_" + tbl + " USING location TAGS (?) VALUES (?, ?, ?, ?)",
                    data.deviceId(), Timestamp.from(data.collectTime()), data.payload().get("lat"), data.payload().get("lng"), data.payload().get("speed"));
        } else if (data.deviceType() == DeviceType.HEALTH_MONITOR) {
            Object heartRate = data.payload().get("heartRate");
            Object breathRate = data.payload().get("breathRate");
            Object isFall = data.payload().get("is_fall");
            Object isPerson = data.payload().get("is_person_present");
            execute("INSERT INTO health_monitor_" + tbl + " USING health_monitor TAGS (?) VALUES (?, ?, ?, ?, ?)",
                    data.deviceId(),
                    Timestamp.from(data.collectTime()),
                    heartRate != null ? heartRate : 0,
                    breathRate != null ? breathRate : 0,
                    isFall != null ? isFall : false,
                    isPerson != null ? isPerson : false
            );
        }
    }

    public void saveDeviceLog(String deviceId, String logType, String content, Instant occurredAt) {
        if (System.currentTimeMillis() < openUntil) {
            return;
        }
        validateIdentifier(deviceId, "deviceId");
        execute("INSERT INTO device_log_" + toTableSuffix(deviceId) + " USING device_log TAGS (?) VALUES (?, ?, ?)",
                deviceId,
                Timestamp.from(occurredAt),
                logType,
                content
        );
    }

    private void execute(String sql, Object... values) {
        try (Connection connection = DriverManager.getConnection(
                appProperties.getTdengine().getUrl(),
                appProperties.getTdengine().getUsername(),
                appProperties.getTdengine().getPassword()
        ); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                preparedStatement.setObject(i + 1, values[i]);
            }
            preparedStatement.execute();
            failCount.set(0);
        } catch (Exception e) {
            if (failCount.incrementAndGet() >= 5) {
                openUntil = System.currentTimeMillis() + 30000;
                failCount.set(0);
            }
        }
    }

    /** 查询指定设备最新一条记录 */
    public Map<String, Object> queryLatestRecord(String tablePrefix, String deviceId) {
        if (System.currentTimeMillis() < openUntil) return null;
        validateIdentifier(tablePrefix, "tablePrefix");
        validateIdentifier(deviceId, "deviceId");
        String sql = "SELECT * FROM " + tablePrefix + "_" + toTableSuffix(deviceId) + " ORDER BY ts DESC LIMIT 1";
        try (Connection connection = DriverManager.getConnection(
                appProperties.getTdengine().getUrl(),
                appProperties.getTdengine().getUsername(),
                appProperties.getTdengine().getPassword()
        ); PreparedStatement ps = connection.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                int columns = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= columns; i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                failCount.set(0);
                return row;
            }
        } catch (Exception e) {
            log.warn("[TDengine] queryLatestRecord {}/{} 失败: {}", tablePrefix, deviceId, e.getMessage());
            if (failCount.incrementAndGet() >= 5) {
                openUntil = System.currentTimeMillis() + 30000;
                failCount.set(0);
            }
        }
        return null;
    }

    public List<Map<String, Object>> queryDeviceData(String tablePrefix, String deviceId, Instant start, Instant end) {
        if (System.currentTimeMillis() < openUntil) {
            return List.of();
        }
        validateIdentifier(tablePrefix, "tablePrefix");
        validateIdentifier(deviceId, "deviceId");
        String sql = "SELECT * FROM " + tablePrefix + "_" + toTableSuffix(deviceId) + " WHERE ts >= ? AND ts <= ? ORDER BY ts";
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                appProperties.getTdengine().getUrl(),
                appProperties.getTdengine().getUsername(),
                appProperties.getTdengine().getPassword()
        ); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setTimestamp(1, Timestamp.from(start));
            preparedStatement.setTimestamp(2, Timestamp.from(end));
            ResultSet rs = preparedStatement.executeQuery();
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columns; i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                result.add(row);
            }
            failCount.set(0);
        } catch (Exception e) {
            if (failCount.incrementAndGet() >= 5) {
                openUntil = System.currentTimeMillis() + 30000;
                failCount.set(0);
            }
        }
        return result;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredData() {
        int days = systemConfigService.getInt("dataRetentionDays", 30);
        log.info("[TDengine] 开始清理 {} 天前的数据", days);
        String[] stables = {"heart_rate", "fall_status", "location", "health_monitor", "device_log"};
        for (String stable : stables) {
            try {
                execute("DELETE FROM pension." + stable + " WHERE ts < NOW() - INTERVAL(" + days + "d)");
            } catch (Exception e) {
                log.warn("[TDengine] 清理 {} 失败: {}", stable, e.getMessage());
            }
        }
        log.info("[TDengine] 数据清理完成");
    }

    public void clearDeviceData(String deviceId) {
        validateIdentifier(deviceId, "deviceId");
        String[] prefixes = {"health_monitor", "fall_status", "device_log"};
        for (String prefix : prefixes) {
            execute("DELETE FROM " + prefix + "_" + deviceId + " WHERE ts > 0");
        }
    }

    public List<Map<String, Object>> queryDeviceLogs(String deviceId, int limit) {
        if (System.currentTimeMillis() < openUntil) {
            return List.of();
        }
        validateIdentifier(deviceId, "deviceId");
        String sql = "SELECT ts, log_type, content FROM device_log_" + toTableSuffix(deviceId) + " ORDER BY ts DESC LIMIT ?";
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                appProperties.getTdengine().getUrl(),
                appProperties.getTdengine().getUsername(),
                appProperties.getTdengine().getPassword()
        ); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, Math.max(limit, 1));
            ResultSet rs = preparedStatement.executeQuery();
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columns; i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                result.add(row);
            }
            failCount.set(0);
        } catch (Exception e) {
            if (failCount.incrementAndGet() >= 5) {
                openUntil = System.currentTimeMillis() + 30000;
                failCount.set(0);
            }
        }
        return result;
    }
}
