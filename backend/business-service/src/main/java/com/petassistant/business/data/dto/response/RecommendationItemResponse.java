package com.petassistant.business.data.dto.response;

/** 可解释推荐项；排序原因不改变帖子或知识信任等级。 */
public record RecommendationItemResponse(
        CommunityPostResponse post, double score, String reason, boolean viewerNotInterested
) { }
