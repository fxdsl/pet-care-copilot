package com.petassistant.business.controller;

import java.util.List;

import com.petassistant.business.data.dto.request.KnowledgeTestRequest;
import com.petassistant.business.data.dto.request.PdfExtractRequest;
import com.petassistant.business.data.dto.response.KnowledgeDocumentSummaryResponse;
import com.petassistant.business.data.dto.response.ChatResponse;
import com.petassistant.business.data.dto.response.KnowledgeReindexResponse;
import com.petassistant.business.data.dto.response.PdfExtractResponse;
import com.petassistant.business.service.KnowledgeService;
import com.petassistant.business.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库 HTTP 控制器，负责文档导入和向量化进度查询。
 */
@RestController
@RequestMapping("/api/v1/knowledge/documents")
public class KnowledgeController {

    private final KnowledgeService service;
    private final ChatService chatService;

    /** 注入知识库业务服务。 */
    public KnowledgeController(KnowledgeService service, ChatService chatService) {
        this.service = service;
        this.chatService = chatService;
    }

    /** 提取 PDF 并返回预览；该步骤不会写入 MySQL。 */
    @PostMapping("/pdf/extract")
    public PdfExtractResponse extractPdf(@Valid @RequestBody PdfExtractRequest request) {
        return service.extractPdf(request);
    }

    /** 用户显式触发全部文档使用当前专业模型重建向量。 */
    @PostMapping("/reindex")
    public KnowledgeReindexResponse reindex() {
        return service.reindexAll();
    }

    /** 查询最近文档及已向量化分块数量。 */
    @GetMapping
    public List<KnowledgeDocumentSummaryResponse> list(@RequestParam(defaultValue = "20") int limit) {
        return service.list(limit);
    }

    /** 管理员独立验证真实 RAG 召回与模型回答，不污染普通用户会话历史。 */
    @PostMapping("/test-answer")
    public ChatResponse testAnswer(@Valid @RequestBody KnowledgeTestRequest request) {
        return chatService.testKnowledge(request);
    }
}
