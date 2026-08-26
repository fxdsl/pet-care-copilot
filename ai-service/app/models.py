"""FastAPI 对外请求与响应模型。

模型集中描述 HTTP 契约，不包含数据库访问或具体检索算法。
"""

from typing import Literal

from pydantic import BaseModel, Field, field_validator, model_validator


class HealthResponse(BaseModel):
    """AI 服务健康状态与当前推理模式。"""

    service: str
    status: str
    version: str
    embedding_model: str
    answer_mode: str
    llm_provider: str
    llm_model: str | None = None


class KnowledgePreprocessRequest(BaseModel):
    """知识清洗、分块和向量化请求。"""

    title: str = Field(min_length=1, max_length=300)
    content: str = Field(min_length=1, max_length=500_000)
    chunk_size: int = Field(default=800, ge=200, le=2_000)
    chunk_overlap: int = Field(default=120, ge=0, le=500)

    @model_validator(mode="after")
    def overlap_must_be_smaller_than_chunk(self) -> "KnowledgePreprocessRequest":
        """相邻重叠不能覆盖整个分块，否则滑动窗口无法前进。"""
        if self.chunk_overlap >= self.chunk_size:
            raise ValueError("chunk_overlap must be smaller than chunk_size")
        return self


class KnowledgeChunk(BaseModel):
    """清洗后的单个知识分块、专业向量及可选 PDF 页码。"""

    chunk_index: int
    content: str
    char_count: int
    token_estimate: int
    embedding: list[float]
    embedding_model: str
    page_start: int | None = None
    page_end: int | None = None


class KnowledgePreprocessResponse(BaseModel):
    """文档预处理结果，供 Spring Boot 原子写入 MySQL。"""

    title: str
    cleaned_content: str
    checksum: str
    chunks: list[KnowledgeChunk]


class KnowledgePrecheckRequest(BaseModel):
    """投稿审核前的轻量检查；不生成向量，也不会给出自动批准结论。"""

    title: str = Field(min_length=1, max_length=300)
    content: str = Field(min_length=1, max_length=500_000)
    source_type: Literal["COMMUNITY_POST", "ADMIN_UPLOAD"]


class KnowledgePrecheckResponse(BaseModel):
    """脱敏、风险标签、摘要和质量分，供人工审核工作台使用。"""

    cleaned_content: str
    checksum: str
    summary: str
    risk_level: Literal["LOW", "MEDIUM", "HIGH"]
    risk_labels: list[str]
    quality_score: float = Field(ge=0, le=100)


class SearchEmbeddingRequest(BaseModel):
    """统一搜索索引与查询共用的免费本地向量请求。"""

    text: str = Field(min_length=1, max_length=10_000)
    mode: Literal["DOCUMENT", "QUERY"] = "QUERY"

    @field_validator("text")
    @classmethod
    def search_text_must_not_be_blank(cls, value: str) -> str:
        """去掉首尾空白并拒绝无意义的模型计算。"""
        normalized = value.strip()
        if not normalized:
            raise ValueError("搜索文本不能为空")
        return normalized


class SearchEmbeddingResponse(BaseModel):
    """本地 BGE 搜索向量；该接口不调用百炼通用模型。"""

    embedding: list[float] = Field(min_length=1)
    embedding_model: str
    dimensions: int = Field(gt=0)


class PdfExtractRequest(BaseModel):
    """浏览器经 Java 传入的 PDF 文件；Base64 上限对应 15 MiB 原文件。"""

    file_name: str = Field(min_length=5, max_length=255)
    content_base64: str = Field(min_length=1, max_length=21_000_000)


class PdfPage(BaseModel):
    """单页文字提取结果，供网页预览和分块页码追踪。"""

    page_number: int
    text: str
    char_count: int


class PdfExtractResponse(BaseModel):
    """PDF 解析结果；扫描型文档返回 OCR_REQUIRED 而不会进入知识库。"""

    file_name: str
    status: Literal["READY", "OCR_REQUIRED"]
    extraction_mode: Literal["TEXT", "SCANNED"]
    page_count: int
    char_count: int
    content: str
    preview: str
    pages: list[PdfPage]


class AgentCandidate(BaseModel):
    """Agent 知识检索工具可访问的候选分块和来源元数据。"""

    chunk_id: str
    document_id: str
    title: str
    source_name: str | None = None
    source_url: str | None = None
    chunk_index: int
    content: str
    embedding: list[float] = Field(min_length=1)
    embedding_model: str
    file_name: str | None = None
    page_start: int | None = None
    page_end: int | None = None


class ConversationMessage(BaseModel):
    """参与本轮回答的历史消息，角色采用 Java 数据库中的大写枚举。"""

    role: Literal["USER", "ASSISTANT"]
    content: str = Field(min_length=1, max_length=4_000)


class PetProfileContext(BaseModel):
    """宠物档案的最小模型上下文，用于个性化回答而非医疗诊断。"""

    name: str = Field(min_length=1, max_length=80)
    pet_type: str = Field(min_length=1, max_length=30)
    breed: str | None = Field(default=None, max_length=100)
    age_months: int | None = Field(default=None, ge=0, le=600)
    weight_kg: float | None = Field(default=None, gt=0, le=9_999.99)
    notes: str | None = Field(default=None, max_length=1_000)


class AgentRequest(BaseModel):
    """第六周 Agent 请求，包含有限历史、授权档案和候选知识。"""

    question: str = Field(min_length=1, max_length=2_000)
    conversation_id: str | None = None
    history: list[ConversationMessage] = Field(default_factory=list, max_length=20)
    pet_profile: PetProfileContext | None = None
    candidates: list[AgentCandidate] = Field(default_factory=list, max_length=2_000)
    top_k: int = Field(default=3, ge=1, le=10)
    min_score: float = Field(default=0.08, ge=-1.0, le=1.0)

    @field_validator("question")
    @classmethod
    def agent_question_must_not_be_blank(cls, value: str) -> str:
        """标准化 Agent 问题，避免无意义模型调用和向量检索。"""
        normalized = value.strip()
        if not normalized:
            raise ValueError("问题不能为空")
        return normalized


class AgentSource(BaseModel):
    """知识检索工具实际返回给 Agent 的可核对来源。"""

    title: str
    url: str | None = None
    chunk_id: str
    score: float
    file_name: str | None = None
    page_start: int | None = None
    page_end: int | None = None


class AgentStep(BaseModel):
    """可展示的 Agent 执行摘要；不包含模型隐藏推理或完整工具正文。"""

    sequence: int
    node: Literal["reason", "tool", "finish", "guard", "fallback"]
    action: str
    tool_name: str | None = None
    status: Literal["SUCCESS", "ERROR", "BLOCKED"]
    summary: str = Field(max_length=500)


class AgentResponse(BaseModel):
    """LangGraph Agent 答案、来源、执行摘要和终止原因。"""

    answer: str
    conversation_id: str | None = None
    sources: list[AgentSource] = Field(default_factory=list)
    stage: str
    model_name: str | None = None
    routing_reason: str
    max_score: float | None = None
    agent_steps: list[AgentStep] = Field(default_factory=list)
    termination_reason: str
    tool_call_count: int = Field(ge=0)
