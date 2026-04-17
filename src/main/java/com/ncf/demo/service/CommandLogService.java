package com.ncf.demo.service;

import com.ncf.demo.entity.CommandLog;
import com.ncf.demo.repository.CommandLogRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CommandLogService {
    private final CommandLogRepository commandLogRepository;

    public CommandLogService(CommandLogRepository commandLogRepository) {
        this.commandLogRepository = commandLogRepository;
    }

    public void record(String deviceId, String commandBody, String responseStatus) {
        CommandLog log = new CommandLog();
        log.setDeviceId(deviceId);
        log.setCommandBody(commandBody);
        log.setResponseStatus(responseStatus);
        log.setSentAt(Instant.now());
        commandLogRepository.save(log);
    }
}
