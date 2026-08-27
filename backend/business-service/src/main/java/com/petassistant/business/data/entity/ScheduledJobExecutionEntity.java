package com.petassistant.business.data.entity;

import java.time.Instant;

/** Quartz 批次执行事实，失败原因和人工处理状态不会只留在日志中。 */
public record ScheduledJobExecutionEntity(
        String id, String jobName, String batchKey, String status, int attempts,
        int processedCount, String errorMessage, Instant startedAt, Instant finishedAt, Instant nextAttemptAt
) { }
