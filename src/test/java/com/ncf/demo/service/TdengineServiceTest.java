package com.ncf.demo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TdengineServiceTest {

    @Test
    void parseDatabaseTargetKeepsQueryStringAndExtractsDatabaseName() {
        TdengineService.DatabaseTarget target = TdengineService.parseDatabaseTarget(
                "jdbc:TAOS-RS://tdengine:6041/pension?timezone=Asia/Shanghai"
        );

        assertEquals("jdbc:TAOS-RS://tdengine:6041/?timezone=Asia/Shanghai", target.baseUrl());
        assertEquals("pension", target.databaseName());
    }

    @Test
    void parseDatabaseTargetSupportsUrlWithoutQueryString() {
        TdengineService.DatabaseTarget target = TdengineService.parseDatabaseTarget(
                "jdbc:TAOS-RS://tdengine:6041/pension"
        );

        assertEquals("jdbc:TAOS-RS://tdengine:6041/", target.baseUrl());
        assertEquals("pension", target.databaseName());
    }

    @Test
    void parseDatabaseTargetRejectsUrlWithoutDatabaseName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TdengineService.parseDatabaseTarget("jdbc:TAOS-RS://tdengine:6041")
        );

        assertEquals(
                "Missing database name in TDengine URL: jdbc:TAOS-RS://tdengine:6041",
                exception.getMessage()
        );
    }
}
