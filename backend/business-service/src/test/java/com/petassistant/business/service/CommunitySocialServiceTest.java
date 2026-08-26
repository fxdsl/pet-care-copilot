package com.petassistant.business.service;

import java.time.Instant;

import com.petassistant.business.data.dto.internal.CommunityCommentView;
import com.petassistant.business.data.dto.internal.CommunityReportView;
import com.petassistant.business.data.dto.request.CreateCommunityCommentRequest;
import com.petassistant.business.data.dto.request.CreateCommunityReportRequest;
import com.petassistant.business.data.dto.request.ModerateCommunityReportRequest;
import com.petassistant.business.data.mapper.CommunityPostMapper;
import com.petassistant.business.data.mapper.CommunitySocialMapper;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.exception.CommunityInteractionConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 第九周互动幂等、两级评论和举报并发边界。 */
@ExtendWith(MockitoExtension.class)
class CommunitySocialServiceTest {

    @Mock CommunitySocialMapper mapper;
    @Mock CommunityPostMapper postMapper;
    @Mock UserMapper userMapper;
    @Mock CommunityPostCacheService postCache;
    @Mock CommunitySocialCacheService socialCache;
    @Mock CommunityRecommendationService recommendationService;
    @Mock MessageService messageService;

    @Test
    void repeatedLikeReturnsFinalDatabaseState() {
        when(mapper.existsPublicPost("post-1")).thenReturn(true);
        when(mapper.existsLike("post-1", "user-1")).thenReturn(true);
        when(mapper.countLikes("post-1")).thenReturn(5L);

        var response = service().like("user-1", "post-1", true);

        assertThat(response.active()).isTrue();
        assertThat(response.count()).isEqualTo(5);
        verify(mapper).insertLike(eq("post-1"), eq("user-1"), any());
        verify(mapper).synchronizeLikeCount("post-1");
        verify(socialCache).synchronizePostRelation("user-1", "post-1", "likes", true, 5L);
    }

    @Test
    void rejectsReplyToSecondLevelComment() {
        when(mapper.existsPublicPost("post-1")).thenReturn(true);
        Instant now = Instant.now();
        when(mapper.findComment("reply-1")).thenReturn(new CommunityCommentView(
                "reply-1", "post-1", "user-2", "bob", "鲍勃", "root-1", "root-1",
                1, "二级回复", "PUBLISHED", 0, now, now
        ));

        assertThatThrownBy(() -> service().createComment(
                "user-1", "post-1", new CreateCommunityCommentRequest("reply-1", "继续回复")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("两级评论");
    }

    @Test
    void duplicateReportUsesStableConflict() {
        when(mapper.existsPublicPost("post-1")).thenReturn(true);
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insertReport(any());

        assertThatThrownBy(() -> service().report("user-1", new CreateCommunityReportRequest(
                "POST", "post-1", "SPAM", "重复广告"
        ))).isInstanceOf(CommunityInteractionConflictException.class)
                .hasMessageContaining("已经举报过");
    }

    @Test
    void hidingReportedPostEvictsRecommendationCopies() {
        Instant now = Instant.now();
        when(mapper.findReport("report-1")).thenReturn(
                report("PENDING", 1, null), report("RESOLVED", 2, "HIDE_CONTENT")
        );
        when(mapper.moderateReport(eq("report-1"), eq("admin-1"), eq("HIDE_CONTENT"), any(), eq(1), any()))
                .thenReturn(1);

        var response = service().moderate(
                "admin-1", "report-1", new ModerateCommunityReportRequest("HIDE_CONTENT", "危险建议", 1)
        );

        assertThat(response.resolution()).isEqualTo("HIDE_CONTENT");
        verify(mapper).hidePost(eq("post-1"), any());
        verify(postCache).evict("post-1");
        verify(recommendationService).remove("post-1");
        verify(socialCache).removeReportFromQueue("report-1");
    }

    private CommunitySocialService service() {
        return new CommunitySocialService(
                mapper, postMapper, userMapper, postCache, socialCache, recommendationService, messageService,
                org.mockito.Mockito.mock(OutboxService.class)
        );
    }

    private static CommunityReportView report(String status, int version, String resolution) {
        Instant now = Instant.now();
        return new CommunityReportView(
                "report-1", "user-1", "alice", "POST", "post-1", "DANGEROUS_ADVICE",
                "疑似危险建议", status, resolution, "admin-1", "admin", "已处理", version, now,
                resolution == null ? null : now
        );
    }
}
