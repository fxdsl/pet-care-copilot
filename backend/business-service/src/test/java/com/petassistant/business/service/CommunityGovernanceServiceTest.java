package com.petassistant.business.service;

import java.time.Instant;

import com.petassistant.business.data.dto.internal.CommunityPostView;
import com.petassistant.business.data.dto.request.CreateCommunityRepostRequest;
import com.petassistant.business.data.entity.CommunityRelationControlEntity;
import com.petassistant.business.data.entity.CommunityRepostEntity;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.CommunityGovernanceMapper;
import com.petassistant.business.data.mapper.CommunityPostMapper;
import com.petassistant.business.data.mapper.CommunitySocialMapper;
import com.petassistant.business.data.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 第十三周转发幂等与拉黑关系副作用测试。 */
@ExtendWith(MockitoExtension.class)
class CommunityGovernanceServiceTest {

    @Mock CommunityGovernanceMapper mapper;
    @Mock CommunityPostMapper postMapper;
    @Mock CommunitySocialMapper socialMapper;
    @Mock UserMapper userMapper;
    @Mock CommunityPostService postService;
    @Mock CommunitySocialService socialService;
    @Mock CommunityPostCacheService postCache;
    @Mock CommunitySocialCacheService socialCache;
    @Mock CommunityGovernanceCacheService cache;
    @Mock OutboxService outboxService;

    @Test
    void repeatedRepostReturnsDatabaseCountInsteadOfIncrementingBlindly() {
        Instant now = Instant.now();
        CommunityRepostEntity saved = new CommunityRepostEntity(
                "repost-1", "post-1", "user-1", null, true, now, now
        );
        when(postMapper.findPublicView("post-1")).thenReturn(post("author-1"));
        when(mapper.findRepost("post-1", "user-1")).thenReturn(saved);
        when(mapper.countReposts("post-1")).thenReturn(1L);

        var result = service().repost("user-1", "post-1", true, new CreateCommunityRepostRequest(null));

        assertThat(result.active()).isTrue();
        assertThat(result.count()).isEqualTo(1);
        verify(mapper).upsertRepost(any());
        verify(mapper).synchronizeRepostCount("post-1");
    }

    @Test
    void enablingBlockRemovesBothFollowDirections() {
        Instant now = Instant.now();
        UserEntity target = new UserEntity(
                "user-2", "bob", "hash", "Bob", "USER", "ACTIVE", 1,
                null, null, null, null, now, now
        );
        CommunityRelationControlEntity relation = new CommunityRelationControlEntity(
                "relation-1", "user-1", "user-2", "BLOCK", true, now, now
        );
        when(userMapper.findById("user-2")).thenReturn(target);
        when(mapper.findRelation("user-1", "user-2", "BLOCK")).thenReturn(null, relation);

        var result = service().relation("user-1", "user-2", "block", true);

        assertThat(result.active()).isTrue();
        verify(socialMapper).deleteFollow("user-1", "user-2");
        verify(socialMapper).deleteFollow("user-2", "user-1");
        verify(cache).synchronizeRelation(relation);
    }

    private CommunityGovernanceService service() {
        return new CommunityGovernanceService(
                mapper, postMapper, socialMapper, userMapper, postService, socialService,
                postCache, socialCache, cache, outboxService
        );
    }

    private static CommunityPostView post(String authorId) {
        Instant now = Instant.now();
        return new CommunityPostView(
                "post-1", authorId, "author", "作者", null, null, null, null,
                "标题", "正文", null, null, null, "PUBLISHED",
                10, 2, 1, 1, 1, 1, now, now, now
        );
    }
}
