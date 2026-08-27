package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;

import com.petassistant.business.data.dto.internal.CommunityPostView;
import com.petassistant.business.data.dto.request.CreateCommunityPostRequest;
import com.petassistant.business.data.entity.CommunityPostEntity;
import com.petassistant.business.data.mapper.CommunityMediaMapper;
import com.petassistant.business.data.mapper.CommunityPostMapper;
import com.petassistant.business.exception.CommunityPostNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 社区草稿创建和 Bloom 明确未命中测试。 */
@ExtendWith(MockitoExtension.class)
class CommunityPostServiceTest {

    @Mock CommunityPostMapper postMapper;
    @Mock CommunityMediaMapper mediaMapper;
    @Mock PetProfileService petProfileService;
    @Mock CommunityPostCacheService cache;
    @Mock OutboxService outboxService;
    @Mock CommunitySocialService socialService;
    @Mock CommunityRecommendationService recommendationService;
    @Mock CommunitySocialCacheService socialCache;

    @Test
    void shouldCreateDraftOwnedByCurrentUser() {
        CommunityPostService service = service();
        when(postMapper.findOwnedView(any(), eq("user-1"))).thenAnswer(invocation -> {
            Instant now = Instant.now();
            return new CommunityPostView(
                    invocation.getArgument(0), "user-1", "alice", "爱丽丝",
                    null, null, null, null, "幼猫换粮", "记录正文", null,
                    null, null,
                    "DRAFT", 0, 0, 0, 0, 0, 1, null, now, now
            );
        });

        var response = service.create("user-1", new CreateCommunityPostRequest(
                "幼猫换粮", "记录正文", null, null, null, null, null, List.of()
        ));

        ArgumentCaptor<CommunityPostEntity> captor = ArgumentCaptor.forClass(CommunityPostEntity.class);
        verify(postMapper).insert(captor.capture());
        assertThat(captor.getValue().authorId()).isEqualTo("user-1");
        assertThat(captor.getValue().status()).isEqualTo("DRAFT");
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    @Test
    void shouldRejectBloomDefiniteMissWithoutQueryingDatabase() {
        CommunityPostService service = service();
        when(cache.mightExist("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.publicDetail("missing"))
                .isInstanceOf(CommunityPostNotFoundException.class);
    }

    @Test
    void shouldApplyTopicFilterToNearbyFeed() {
        CommunityPostService service = service();
        when(recommendationService.nearby(30.2741, 120.1551, 20, "topic-1", 0, 12))
                .thenReturn(new CommunityRecommendationService.FeedSlice(List.of(), 0));
        when(socialService.decoratePosts("user-1", List.of())).thenReturn(List.of());

        var response = service.publicFeed(
                "user-1", "NEARBY", "topic-1", null,
                30.2741, 120.1551, 20, 0, 12
        );

        assertThat(response.items()).isEmpty();
        verify(recommendationService).nearby(30.2741, 120.1551, 20, "topic-1", 0, 12);
    }

    private CommunityPostService service() {
        return new CommunityPostService(
                postMapper, mediaMapper, petProfileService, cache, outboxService,
                socialService, recommendationService, socialCache
        );
    }
}
