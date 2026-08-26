package com.petassistant.business.data.mapper;

import java.time.Instant;
import java.util.List;

import com.petassistant.business.data.entity.OutboxEventEntity;
import org.apache.ibatis.annotations.Param;

/** 可靠事件 Outbox Mapper。 */
public interface OutboxEventMapper {

    int insert(OutboxEventEntity event);

    List<OutboxEventEntity> findDue(@Param("now") Instant now, @Param("limit") int limit);

    int claim(@Param("id") String id, @Param("retryAt") Instant retryAt);

    int markPublished(@Param("id") String id, @Param("publishedAt") Instant publishedAt);

    int markFailed(@Param("id") String id, @Param("nextAttemptAt") Instant nextAttemptAt);
}
