package com.petassistant.business.data.dto.internal;

import java.util.List;

/** 单一内容类型的 OpenSearch 分页结果。 */
public record OpenSearchPage(long total, List<OpenSearchHit> items) { }
