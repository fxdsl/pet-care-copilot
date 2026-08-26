package com.petassistant.business.data.dto.internal;

import java.util.List;

/** OpenSearch 命中及可解释的匹配字段。 */
public record OpenSearchHit(
        SearchSourceDocument source,
        double score,
        List<String> matchedFields
) { }
