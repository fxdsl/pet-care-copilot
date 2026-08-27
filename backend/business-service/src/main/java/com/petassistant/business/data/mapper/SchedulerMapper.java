package com.petassistant.business.data.mapper;

import java.time.Instant;

import com.petassistant.business.data.entity.ScheduledJobExecutionEntity;
import org.apache.ibatis.annotations.Param;

/** 第十三周 Quartz 任务审计、趋势快照和补偿操作 Mapper。 */
public interface SchedulerMapper {

    int insertExecution(ScheduledJobExecutionEntity execution);

    int finishExecution(
            @Param("id") String id,
            @Param("status") String status,
            @Param("attempts") int attempts,
            @Param("processedCount") int processedCount,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") Instant finishedAt
    );

    int insertTrendSnapshot(@Param("snapshotAt") Instant snapshotAt, @Param("limit") int limit);
    int expireKnowledge(@Param("now") Instant now);
    int retryStuckSearchEvents(@Param("now") Instant now);
    int enqueueOutboxManualReviews(@Param("now") Instant now);
    int archiveAdminAudits(@Param("cutoff") Instant cutoff, @Param("archivedAt") Instant archivedAt);
    int deleteArchivedAdminAudits(@Param("cutoff") Instant cutoff);
}
