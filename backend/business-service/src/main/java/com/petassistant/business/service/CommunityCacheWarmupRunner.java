package com.petassistant.business.service;

import com.petassistant.business.data.mapper.CommunityPostMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 服务启动后用 MySQL 已发布帖子重建 Bloom Filter、热门榜和 GEO 副本。 */
@Component
public class CommunityCacheWarmupRunner implements ApplicationRunner {

    private final CommunityPostMapper mapper;
    private final CommunityPostCacheService cache;
    private final CommunityRecommendationService recommendationService;

    public CommunityCacheWarmupRunner(
            CommunityPostMapper mapper,
            CommunityPostCacheService cache,
            CommunityRecommendationService recommendationService
    ) {
        this.mapper = mapper;
        this.cache = cache;
        this.recommendationService = recommendationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        cache.initializeBloom(mapper.findAllPublishedIds());
        recommendationService.rebuild();
    }
}
