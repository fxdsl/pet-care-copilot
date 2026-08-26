"""AI 健康、PDF、知识预处理和 Agent 回答路由。"""

import asyncio
import json
import time

from functools import lru_cache

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse

from app.config import Settings, get_settings
from app.models import (
    AgentRequest,
    AgentResponse,
    HealthResponse,
    KnowledgePrecheckRequest,
    KnowledgePrecheckResponse,
    KnowledgePreprocessRequest,
    KnowledgePreprocessResponse,
    PdfExtractRequest,
    PdfExtractResponse,
    SearchEmbeddingRequest,
    SearchEmbeddingResponse,
)
from app.services.agent_service import PetAgentService
from app.services.embedding_service import FastEmbedEmbeddingService
from app.services.knowledge_service import KnowledgePreprocessService
from app.services.pdf_service import PdfExtractionError, PdfExtractionService

router = APIRouter(prefix="/api/v1/ai", tags=["ai"])
knowledge_router = APIRouter(prefix="/api/v1/knowledge", tags=["knowledge"])
agent_router = APIRouter(prefix="/api/v1/agent", tags=["agent"])

# 进程级并发门限制同时运行的 Agent 流，保护免费模型额度和工作线程。
agent_stream_slots = asyncio.Semaphore(16)


@lru_cache
def get_embedding_service() -> FastEmbedEmbeddingService:
    """全进程复用同一个专业向量模型，避免每个请求重复加载 ONNX。"""
    settings = get_settings()
    return FastEmbedEmbeddingService(
        dimensions=settings.embedding_dimensions,
        model_name=settings.embedding_model,
        cache_dir=settings.embedding_cache_dir,
    )


def get_knowledge_service(
    embedding_service: FastEmbedEmbeddingService = Depends(get_embedding_service),
) -> KnowledgePreprocessService:
    """创建带本地向量能力的知识预处理服务。"""
    return KnowledgePreprocessService(embedding_service)


def get_agent_service(
    settings: Settings = Depends(get_settings),
    embedding_service: FastEmbedEmbeddingService = Depends(get_embedding_service),
) -> PetAgentService:
    """创建 Agent 服务，并复用与文档导入一致的向量配置。"""
    return PetAgentService(settings=settings, embedding_service=embedding_service)


@router.get("/health", response_model=HealthResponse)
def health(settings: Settings = Depends(get_settings)) -> HealthResponse:
    """返回服务状态、向量模型和当前回答模式。"""
    return HealthResponse(
        service=settings.app_name,
        status="UP",
        version=settings.app_version,
        embedding_model=settings.embedding_model,
        answer_mode=settings.answer_mode,
        llm_provider=settings.llm_provider,
        llm_model=settings.llm_chat_model if settings.llm_enabled else None,
    )


@knowledge_router.post("/preprocess", response_model=KnowledgePreprocessResponse)
def preprocess_knowledge(
    # 1. 接收知识预处理请求
    request: KnowledgePreprocessRequest,
    # 2. 调用知识预处理服务
    service: KnowledgePreprocessService = Depends(get_knowledge_service),
) -> KnowledgePreprocessResponse:
    """清洗、分块、生成 checksum 和本地向量。"""
    return service.preprocess(request)


@knowledge_router.post("/precheck", response_model=KnowledgePrecheckResponse)
def precheck_knowledge(
    request: KnowledgePrecheckRequest,
    service: KnowledgePreprocessService = Depends(get_knowledge_service),
) -> KnowledgePrecheckResponse:
    """对投稿进行隐私、广告、危险建议和提示注入预检，不自动批准。"""
    return service.precheck(request)


@knowledge_router.post("/search/embed", response_model=SearchEmbeddingResponse)
def embed_for_search(
    request: SearchEmbeddingRequest,
    embedding_service: FastEmbedEmbeddingService = Depends(get_embedding_service),
) -> SearchEmbeddingResponse:
    """生成索引文档或搜索词向量，全程使用免费本地 ONNX 模型。"""
    vector = (
        embedding_service.embed_document(request.text)
        if request.mode == "DOCUMENT"
        else embedding_service.embed_query(request.text)
    )
    return SearchEmbeddingResponse(
        embedding=vector,
        embedding_model=embedding_service.model_name,
        dimensions=len(vector),
    )


@knowledge_router.post("/pdf/extract", response_model=PdfExtractResponse)
def extract_pdf(request: PdfExtractRequest) -> PdfExtractResponse:
    """提取文字型 PDF；扫描件返回 OCR_REQUIRED，非法文件返回 422。"""
    try:
        return PdfExtractionService().extract(request)
    except PdfExtractionError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error


@agent_router.post("/answer", response_model=AgentResponse)
def answer_with_agent(
    request: AgentRequest,
    service: PetAgentService = Depends(get_agent_service),
) -> AgentResponse:
    """运行受控 Agent，并返回答案、来源和可展示的执行摘要。"""
    return service.answer(request)


@agent_router.post("/answer/stream")
async def stream_answer_with_agent(
    request: AgentRequest,
    service: PetAgentService = Depends(get_agent_service),
) -> StreamingResponse:
    """以脱敏 SSE 事件输出 Agent 状态、答案片段和最终结构化结果。"""

    async def events():  # noqa: ANN202
        event_id = 0

        def encode(event: str, payload: dict[str, object]) -> str:
            """生成标准 SSE 帧；payload 只包含允许向用户公开的运行状态。"""
            nonlocal event_id
            event_id += 1
            data = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
            return f"id: {event_id}\nevent: {event}\ndata: {data}\n\n"

        async with agent_stream_slots:
            yield encode("stage", {"stage": "STARTED", "message": "已接收问题，正在运行受控 Agent"})
            task = asyncio.create_task(asyncio.to_thread(service.answer, request))
            started_at = time.monotonic()
            while not task.done():
                if time.monotonic() - started_at > 120:
                    task.cancel()
                    yield encode("error", {"code": "AGENT_TIMEOUT", "message": "Agent 执行超过 120 秒，已停止等待"})
                    return
                try:
                    await asyncio.wait_for(asyncio.shield(task), timeout=5)
                except TimeoutError:
                    yield encode("heartbeat", {"stage": "RUNNING", "message": "正在检索资料并调用模型"})

            response = task.result()
            # Agent 完成后按实际脱敏步骤补发节点事件，不输出隐藏思维或工具观察正文。
            for step in response.agent_steps:
                yield encode("stage", {
                    "stage": step.node.upper(),
                    "action": step.action,
                    "status": step.status,
                    "message": step.summary,
                })
            for offset in range(0, len(response.answer), 12):
                yield encode("token", {"text": response.answer[offset:offset + 12]})
                await asyncio.sleep(0)
            yield encode("result", json.loads(response.model_dump_json()))
            yield encode("done", {"terminationReason": response.termination_reason})

    return StreamingResponse(
        events(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "X-Accel-Buffering": "no",
        },
    )
