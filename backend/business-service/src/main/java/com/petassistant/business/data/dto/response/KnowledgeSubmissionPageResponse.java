package com.petassistant.business.data.dto.response;

import java.util.List;

/** 知识投稿分页响应。 */
public record KnowledgeSubmissionPageResponse(
        List<KnowledgeSubmissionResponse> items,
        int page,
        int size,
        long total
) { }
