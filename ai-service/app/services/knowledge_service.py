"""知识正文清洗、页码感知分块和专业向量化服务。"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass

from app.models import (
    KnowledgeChunk,
    KnowledgePrecheckRequest,
    KnowledgePrecheckResponse,
    KnowledgePreprocessRequest,
    KnowledgePreprocessResponse,
)
from app.services.embedding_service import EmbeddingProvider


@dataclass(frozen=True)
class TextChunk:
    """预向量化分块，PDF 来源额外携带起止页码。"""

    content: str
    page_start: int | None = None
    page_end: int | None = None


class KnowledgePreprocessService:
    """清洗文本、生成重叠分块并使用同一专业模型生成文档向量。"""

    _sentence_boundary = re.compile(r"(?<=[。！？!?；;\n])")
    _page_marker = re.compile(r"(?m)^\[PDF_PAGE:(\d+)]\s*$")

    def __init__(self, embedding_service: EmbeddingProvider) -> None:
        self.embedding_service = embedding_service

    def preprocess(self, request: KnowledgePreprocessRequest) -> KnowledgePreprocessResponse:
        """执行清洗、页码感知分块、checksum 和本地专业向量化。

        这是知识预处理的入口方法，负责将原始文档转换为可直接存入向量数据库的
        结构化数据。完整流程包括：文本清洗 → 智能分块 → 内容完整性校验 → 向量化 → 组装响应。

        Args:
            request: 包含原始内容、分块参数（大小/重叠）和标题的预处理请求

        Returns:
            包含清洗正文、SHA-256 校验和、带向量的分块列表的结构化响应

        Raises:
            ValueError: 清洗后内容为空或未产生有效分块时抛出
        """
        # 阶段 1：调用内部分块引擎，根据内容类型自动选择处理策略
        # - 如果包含 [PDF_PAGE:n] 标记 → 按页隔离分块（保留页码追溯）
        # - 如果是普通 Markdown/TXT → 全文滑动窗口分块
        # 返回值：cleaned 为清洗后的完整正文，chunks 为分块对象列表
        cleaned, chunks = self._prepare_chunks(
            request.content,          # 原始文档内容（可能含 PDF 页码标记）
            request.chunk_size,       # 单个分块的最大字符数（如 500 字符）
            request.chunk_overlap     # 相邻分块之间的重叠字符数（如 50 字符）
        )

        # 阶段 2：空值防御 — 确保清洗后的内容有效且产生了至少一个分块
        # 边界情况：原文全是空白/乱码/BOM 标记等不可用内容时会触发此检查
        if not cleaned or not chunks:
            raise ValueError("content is empty after cleaning")

        # 阶段 3：生成 SHA-256 内容指纹（Checksum）
        # 用途：
        #   - 去重：相同内容的文档不会重复导入
        #   - 增量更新：checksum 变化时才触发重新向量化
        #   - 审计追踪：记录导入时的精确内容状态
        #   - 缓存键：可作为 Redis 或数据库缓存的唯一标识
        checksum = hashlib.sha256(cleaned.encode("utf-8")).hexdigest()

        # 阶段 4：批量生成向量嵌入（Embedding）— RAG 检索的核心基础
        # 将每个分块的文本语义映射为固定维度的浮点向量（如 384/768/1536 维）
        # 批量调用比逐个调用更高效，减少网络开销和模型推理延迟
        embeddings = self.embedding_service.embed_documents(
            [chunk.content for chunk in chunks]  # 提取所有分块的纯文本内容组成列表
        )

        # 阶段 5：组装结构化响应对象，将所有处理结果封装为可序列化的数据结构
        return KnowledgePreprocessResponse(
            title=request.title.strip(),                    # 知识条目标题（去除首尾空白符）

            cleaned_content=cleaned,                        # 清洗后的完整正文
                                                            # PDF 格式保留 [PDF_PAGE:n] 标记用于展示

            checksum=checksum,                              # SHA-256 哈希值（64位十六进制字符串）

            chunks=[
                # 将每个 TextChunk 转换为完整的 KnowledgeChunk 数据对象
                KnowledgeChunk(
                    chunk_index=index,                      # 分块序号（0-based），作为主键标识

                    content=chunk.content,                  # 分块的原始文本内容（已清洗）

                    char_count=len(chunk.content),          # 分块字符数统计，用于监控和展示

                    token_estimate=max(1, (len(chunk.content) + 1) // 2),
                                                            # Token 数估算公式：
                                                            # 中文字符约 2 字符 = 1 token
                                                            # max(1, ...) 确保至少返回 1，避免零值异常
                                                            # 用于 LLM 计费配额控制和上下文窗口管理

                    embedding=embeddings[index],            # 该分块对应的向量嵌入
                                                            # 维度取决于模型（如 BGE-M3 为 1024 维）
                                                            # 用于后续余弦相似度检索计算

                    embedding_model=self.embedding_service.model_name,
                                                            # 记录使用的 Embedding 模型名称
                                                            # 如 "BAAI/bge-m3" 或 "text-embedding-3-small"
                                                            # 重要：模型切换时需重新向量化，此字段用于版本追踪

                    page_start=chunk.page_start,            # PDF 来源起始页码
                                                            # None 表示非 PDF 来源（Markdown/TXT）
                                                            # 用于回答时引用"来自原文件第 X 页"

                    page_end=chunk.page_end,                # PDF 来源结束页码
                                                            # 通常与 page_start 相同（单页分块）
                                                            # 未来支持跨页分块时可不同
                )
                for index, chunk in enumerate(chunks)       # 遍历所有分块，index 与 embeddings 一一对应
            ],
        )

    def precheck(self, request: KnowledgePrecheckRequest) -> KnowledgePrecheckResponse:
        """执行可解释的审核预检，所有风险只作为人工审核辅助。

        预检不调用付费通用模型，避免消耗百炼免费额度；规则结果稳定、可测试，
        同时不会直接把用户内容写入向量库。正式发布仍必须经过管理员批准。
        """
        cleaned = self.clean_text(request.content)
        if not cleaned:
            raise ValueError("content is empty after cleaning")
        checksum = hashlib.sha256(cleaned.encode("utf-8")).hexdigest()
        labels: list[str] = []

        # 手机号、邮箱和身份证样式只标记不回显具体值，避免审核接口扩大隐私暴露。
        if re.search(r"(?<!\d)1[3-9]\d{9}(?!\d)", cleaned) or re.search(
            r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}", cleaned
        ):
            labels.append("PRIVACY")
        if re.search(r"(?<!\d)\d{17}[\dXx](?!\d)", cleaned):
            labels.append("PRIVACY")
        if re.search(r"(加微|微信|vx|代购|下单|优惠券|返现|联系电话)", cleaned, re.IGNORECASE):
            labels.append("ADVERTISEMENT")
        if re.search(r"(忽略.{0,12}(指令|规则)|system prompt|开发者消息|泄露提示词|越狱)", cleaned, re.IGNORECASE):
            labels.append("PROMPT_INJECTION")
        if re.search(r"(自行注射|随意停药|无需就医|保证治愈|安乐死教程|投毒|虐待)", cleaned, re.IGNORECASE):
            labels.append("DANGEROUS_ADVICE")
        if len(cleaned) < 80:
            labels.append("TOO_SHORT")
        if request.source_type == "COMMUNITY_POST":
            labels.append("USER_EXPERIENCE")

        high_labels = {"PRIVACY", "PROMPT_INJECTION", "DANGEROUS_ADVICE"}
        risk_level = "HIGH" if high_labels.intersection(labels) else "MEDIUM" if labels else "LOW"
        quality = 90.0
        quality -= 25.0 if "TOO_SHORT" in labels else 0.0
        quality -= 20.0 * len(high_labels.intersection(labels))
        quality -= 10.0 if "ADVERTISEMENT" in labels else 0.0
        quality = max(0.0, min(100.0, quality))

        # 摘要采用确定性截断，不向外部模型发送可能含隐私的投稿原文。
        summary = cleaned[:220] + ("…" if len(cleaned) > 220 else "")
        return KnowledgePrecheckResponse(
            cleaned_content=cleaned,
            checksum=checksum,
            summary=summary,
            risk_level=risk_level,
            risk_labels=labels,
            quality_score=quality,
        )

    def _prepare_chunks(self, content: str, chunk_size: int, overlap: int) -> tuple[str, list[TextChunk]]:
        """PDF 按页分别分块；普通 Markdown/TXT 继续使用全文滑窗。"""
        page_sections = self._extract_pdf_pages(content)
        if not page_sections:
            cleaned = self.clean_text(content)
            return cleaned, [TextChunk(chunk) for chunk in self.split_text(cleaned, chunk_size, overlap)]

        cleaned_sections: list[tuple[int, str]] = []
        chunks: list[TextChunk] = []
        for page_number, page_text in page_sections:
            cleaned_page = self.clean_text(page_text)
            if not cleaned_page:
                continue
            cleaned_sections.append((page_number, cleaned_page))
            chunks.extend(
                TextChunk(chunk, page_number, page_number)
                for chunk in self.split_text(cleaned_page, chunk_size, overlap)
            )
        cleaned = "\n\n".join(
            f"[PDF_PAGE:{page_number}]\n{text}" for page_number, text in cleaned_sections
        )
        return cleaned, chunks

    def _extract_pdf_pages(self, content: str) -> list[tuple[int, str]]:
        """解析 PDF 服务生成的稳定页码标记，拒绝重复或倒序页码。"""
        matches = list(self._page_marker.finditer(content))
        if not matches:
            return []
        sections: list[tuple[int, str]] = []
        last_page = 0
        for index, match in enumerate(matches):
            page_number = int(match.group(1))
            if page_number <= last_page:
                raise ValueError("PDF page markers must be strictly increasing")
            end = matches[index + 1].start() if index + 1 < len(matches) else len(content)
            sections.append((page_number, content[match.end():end]))
            last_page = page_number
        return sections

    @staticmethod
    def clean_text(content: str) -> str:
        """去除 BOM、统一换行、压缩空白，同时保留一个段落空行。"""
        text = content.replace("\ufeff", "").replace("\u00a0", " ")
        text = text.replace("\r\n", "\n").replace("\r", "\n")
        lines = [re.sub(r"[ \t]+", " ", line).strip() for line in text.split("\n")]
        compact: list[str] = []
        previous_blank = False
        for line in lines:
            if line:
                compact.append(line)
                previous_blank = False
            elif compact and not previous_blank:
                compact.append("")
                previous_blank = True
        return "\n".join(compact).strip()

    def split_text(self, text: str, chunk_size: int, overlap: int) -> list[str]:
        """优先沿句子边界切分，并为相邻分块保留有限上下文重叠。"""
        if not text:
            return []
        if len(text) <= chunk_size:
            return [text]
        chunks: list[str] = []
        start = 0
        while start < len(text):
            maximum_end = min(start + chunk_size, len(text))
            end = maximum_end
            if maximum_end < len(text):
                candidates = list(self._sentence_boundary.finditer(text, start, maximum_end))
                acceptable = [item.end() for item in candidates if item.end() >= start + chunk_size // 2]
                if acceptable:
                    end = acceptable[-1]
            chunk = text[start:end].strip()
            if chunk:
                chunks.append(chunk)
            if end >= len(text):
                break
            next_start = max(end - overlap, start + 1)
            while next_start < end and text[next_start].isspace():
                next_start += 1
            start = next_start
        return chunks
