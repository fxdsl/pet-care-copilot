package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;

import com.petassistant.business.client.KnowledgeAiClient;
import com.petassistant.business.data.dto.internal.CommunityPostView;
import com.petassistant.business.data.dto.request.CreateCommunityKnowledgeSubmissionRequest;
import com.petassistant.business.data.dto.request.ReviewKnowledgeSubmissionRequest;
import com.petassistant.business.data.entity.KnowledgeSubmissionEntity;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.CommunityPostMapper;
import com.petassistant.business.data.mapper.KnowledgeMapper;
import com.petassistant.business.data.mapper.KnowledgeSubmissionMapper;
import com.petassistant.business.data.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 第十一周投稿授权、状态机和高风险发布边界测试。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSubmissionServiceTest {

    @Mock private KnowledgeSubmissionMapper mapper;
    @Mock private KnowledgeMapper knowledgeMapper;
    @Mock private CommunityPostMapper postMapper;
    @Mock private UserMapper userMapper;
    @Mock private KnowledgeAiClient aiClient;
    @Mock private KnowledgeService knowledgeService;
    @Mock private OutboxService outboxService;
    @Mock private MessageService messageService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedissonClient redissonClient;

    private KnowledgeSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeSubmissionService(
                mapper, knowledgeMapper, postMapper, userMapper, aiClient, knowledgeService,
                outboxService, messageService, redisTemplate, redissonClient
        );
    }

    /** 只有自己的已发布帖子可创建版本快照，并在同一事务写预检 Outbox。 */
    @Test
    void shouldSubmitOwnedPublishedPostForAsynchronousPrecheck() {
        Instant now = Instant.now();
        CommunityPostView post = new CommunityPostView(
                "post-1", "user-1", "alice", "爱猫人", null, null, null, null,
                "幼猫饮水经验", "每天清洗水碗并观察饮水变化。", null, null, null,
                "PUBLISHED", 0, 0, 0, 0, 0, 1, now, now, now
        );
        KnowledgeSubmissionEntity[] stored = new KnowledgeSubmissionEntity[1];
        when(postMapper.findOwnedView("post-1", "user-1")).thenReturn(post);
        when(mapper.findBySource("COMMUNITY_POST", "post-1")).thenReturn(null);
        when(mapper.insert(any())).thenAnswer(invocation -> { stored[0] = invocation.getArgument(0); return 1; });
        when(mapper.findById(anyString())).thenAnswer(invocation -> stored[0]);
        when(mapper.findTimeline(anyString())).thenReturn(List.of());
        when(userMapper.findById("user-1")).thenReturn(user("user-1", "alice"));

        var response = service.submitCommunity(
                "user-1", new CreateCommunityKnowledgeSubmissionRequest("post-1", "CAT", "FEEDING", true)
        );

        assertThat(response.status()).isEqualTo("PRECHECKING");
        assertThat(response.consentStatus()).isEqualTo("GRANTED");
        verify(mapper).insertVersion(any());
        verify(outboxService).record(
                "KNOWLEDGE_SUBMISSION", response.id(), "KNOWLEDGE_PRECHECK_REQUESTED", "user-1"
        );
    }

    /** 社区经验固定为 C 级，健康类内容不能被管理员强行批准进入 RAG。 */
    @Test
    void shouldBlockCommunityHealthAdviceFromRagPublication() {
        KnowledgeSubmissionEntity pending = submission("PENDING_REVIEW", "HEALTH", "LOW");
        when(mapper.findById("submission-1")).thenReturn(pending);

        assertThatThrownBy(() -> service.review(
                "admin-1", "submission-1", new ReviewKnowledgeSubmissionRequest("APPROVE", 1, "A", "通过")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("健康和疫苗知识");
        verify(mapper, never()).approve(anyString(), anyInt(), anyString(), any());
    }

    private static KnowledgeSubmissionEntity submission(String status, String category, String riskLevel) {
        Instant now = Instant.now();
        return new KnowledgeSubmissionEntity(
                "submission-1", "COMMUNITY_POST", "post-1", "user-1", "经验", "社区", "alice",
                null, null, "TEXT", "CAT", category, "经验正文", "经验正文", "a".repeat(64),
                "GRANTED", status, riskLevel, "USER_EXPERIENCE", "摘要", null, 1,
                null, null, null, now, null, null, null, now, now
        );
    }

    private static UserEntity user(String id, String username) {
        Instant now = Instant.now();
        return new UserEntity(id, username, "hash", username, "USER", "ACTIVE", 1,
                null, null, null, null, now, now);
    }
}
