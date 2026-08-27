package com.petassistant.business.client;

import com.petassistant.business.data.dto.internal.AiKnowledgePreprocessRequest;
import com.petassistant.business.data.dto.internal.AiKnowledgePreprocessResponse;
import com.petassistant.business.data.dto.internal.AiKnowledgePrecheckRequest;
import com.petassistant.business.data.dto.internal.AiKnowledgePrecheckResponse;
import com.petassistant.business.data.dto.internal.AiPdfExtractRequest;
import com.petassistant.business.data.dto.internal.AiPdfExtractResponse;
import com.petassistant.business.data.dto.internal.AiSearchEmbeddingRequest;
import com.petassistant.business.data.dto.internal.AiSearchEmbeddingResponse;
import com.petassistant.business.data.dto.internal.KnowledgeProcessInput;
import com.petassistant.business.data.dto.request.PdfExtractRequest;
import com.petassistant.business.exception.AiServiceUnavailableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * FastAPI 知识预处理客户端，负责清洗、分块和本地向量化协议转换。
 */
@Component
public class KnowledgeAiClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAiClient.class);

    private final RestClient aiRestClient;

    /** 注入统一配置的 FastAPI RestClient。 */
    public KnowledgeAiClient(@Qualifier("aiRestClient") RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    /** 将审核通过的内部发布输入转换为 Python 契约并调用预处理接口。 */
    @Retry(name = "aiService")
    @CircuitBreaker(name = "aiService")
    @Bulkhead(name = "aiService", type = Bulkhead.Type.SEMAPHORE)
    public AiKnowledgePreprocessResponse preprocess(KnowledgeProcessInput request) {
        AiKnowledgePreprocessRequest aiRequest = new AiKnowledgePreprocessRequest(
                request.title(), request.content(), request.resolvedChunkSize(), request.resolvedChunkOverlap()
        );
        try {
            AiKnowledgePreprocessResponse response = aiRestClient.post()
                    .uri("/api/v1/knowledge/preprocess")
                    .body(aiRequest)
                    .retrieve()
                    .body(AiKnowledgePreprocessResponse.class);
            if (response == null) {
                throw new IllegalStateException("AI 服务返回空响应");
            }
            return response;
        } catch (Exception error) {
            log.warn("Knowledge preprocessing call failed: {}", error.toString());
            throw new AiServiceUnavailableException(error);
        }
    }

    /** 审核预检只做规则化清洗与风险标注，不生成向量、不消耗通用模型额度。 */
    @CircuitBreaker(name = "aiService")
    @Bulkhead(name = "aiService", type = Bulkhead.Type.SEMAPHORE)
    public AiKnowledgePrecheckResponse precheck(String title, String content, String sourceType) {
        try {
            AiKnowledgePrecheckResponse response = aiRestClient.post()
                    .uri("/api/v1/knowledge/precheck")
                    .body(new AiKnowledgePrecheckRequest(title, content, sourceType))
                    .retrieve()
                    .body(AiKnowledgePrecheckResponse.class);
            if (response == null) throw new IllegalStateException("AI 服务返回空预检结果");
            return response;
        } catch (RestClientResponseException responseError) {
            int status = responseError.getStatusCode().value();
            String message = responseError.getStatusCode().is4xxClientError()
                    ? "AI 预检请求校验失败（HTTP " + status + "）"
                    : "AI 预检服务响应异常（HTTP " + status + "）";
            // 不记录响应 Body，Pydantic 的校验详情可能回显投稿原文。
            log.warn("Knowledge precheck rejected with HTTP {}", status);
            throw new AiServiceUnavailableException(message, responseError);
        } catch (Exception error) {
            log.warn("Knowledge precheck call failed: {}", error.toString());
            throw new AiServiceUnavailableException(error);
        }
    }

    /** 统一搜索只调用免费本地 BGE，不会消耗百炼通用模型额度。 */
    @Retry(name = "aiService")
    @CircuitBreaker(name = "aiService")
    @Bulkhead(name = "aiService", type = Bulkhead.Type.SEMAPHORE)
    public AiSearchEmbeddingResponse embedForSearch(String text, boolean documentMode) {
        try {
            AiSearchEmbeddingResponse response = aiRestClient.post()
                    .uri("/api/v1/knowledge/search/embed")
                    .body(new AiSearchEmbeddingRequest(text, documentMode ? "DOCUMENT" : "QUERY"))
                    .retrieve()
                    .body(AiSearchEmbeddingResponse.class);
            if (response == null || response.embedding() == null || response.embedding().isEmpty()) {
                throw new IllegalStateException("AI 服务返回空搜索向量");
            }
            return response;
        } catch (Exception error) {
            log.warn("Search embedding call failed: {}", error.toString());
            throw new AiServiceUnavailableException(error);
        }
    }

    /** 转发 PDF 到 FastAPI 做安全校验、逐页提取和扫描件识别。 */
    @Retry(name = "aiService")
    @CircuitBreaker(name = "aiService")
    @Bulkhead(name = "aiService", type = Bulkhead.Type.SEMAPHORE)
    public AiPdfExtractResponse extractPdf(PdfExtractRequest request) {
        try {
            AiPdfExtractResponse response = aiRestClient.post()
                    .uri("/api/v1/knowledge/pdf/extract")
                    .body(new AiPdfExtractRequest(request.fileName(), request.contentBase64()))
                    .retrieve()
                    .body(AiPdfExtractResponse.class);
            if (response == null) {
                throw new IllegalStateException("AI 服务返回空 PDF 提取结果");
            }
            return response;
        } catch (Exception error) {
            log.warn("PDF extraction call failed: {}", error.toString());
            throw new AiServiceUnavailableException(error);
        }
    }
}
