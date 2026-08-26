package com.petassistant.business.data.mapper;

import java.time.Instant;
import java.util.List;

import com.petassistant.business.data.entity.CommunityMediaEntity;
import org.apache.ibatis.annotations.Param;

/** 社区媒体元数据 MyBatis Mapper。 */
public interface CommunityMediaMapper {

    int insert(CommunityMediaEntity media);

    CommunityMediaEntity findOwned(@Param("id") String id, @Param("ownerId") String ownerId);

    CommunityMediaEntity findAccessible(@Param("id") String id, @Param("userId") String userId);

    List<CommunityMediaEntity> findByPostId(@Param("postId") String postId);

    long countAttachable(@Param("ownerId") String ownerId, @Param("mediaIds") List<String> mediaIds);

    int detachFromPost(@Param("postId") String postId, @Param("ownerId") String ownerId);

    int attachToPost(
            @Param("postId") String postId,
            @Param("ownerId") String ownerId,
            @Param("mediaIds") List<String> mediaIds
    );

    int markConfirmed(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("confirmedAt") Instant confirmedAt
    );

    int markProcessingReady(@Param("id") String id);
}
