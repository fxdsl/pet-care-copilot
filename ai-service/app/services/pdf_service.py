"""文字型 PDF 提取和扫描件识别服务。"""

from __future__ import annotations

import base64
import binascii
import re
from io import BytesIO

from pypdf import PdfReader

from app.models import PdfExtractRequest, PdfExtractResponse, PdfPage


class PdfExtractionError(ValueError):
    """表示文件格式、加密、大小或页数不符合导入约束。"""


class PdfExtractionService:
    """安全提取文字型 PDF；扫描件只标记状态，不伪造 OCR 结果。"""

    MAX_FILE_BYTES = 15 * 1024 * 1024
    MAX_PAGES = 100
    MAX_TEXT_CHARS = 500_000
    MIN_EXTRACTED_CHARS = 20

    def extract(self, request: PdfExtractRequest) -> PdfExtractResponse:
        """校验并逐页提取文本，生成带页码标记的待导入正文。

        处理流程：
        1. 输入合法性校验（扩展名、Base64、大小、魔数）
        2. PDF 结构解析（pypdf 解密/页面检查）
        3. 逐页文本提取与清洗
        4. 智能判断：文字型 PDF vs 扫描件（OCR_REQUIRED）
        5. 组装带 [PDF_PAGE:n] 标记的最终正文
        """
        # 第一层防御：扩展名白名单校验，防止非 PDF 文件进入解析流程
        if not request.file_name.lower().endswith(".pdf"):
            raise PdfExtractionError("只允许解析 .pdf 文件")

        # 将前端上传的 Base64 编码字符串解码为原始字节流
        # validate=True 会严格检查 Base64 字符的合法性（如非法字符、长度不对等）
        try:
            raw = base64.b64decode(request.content_base64, validate=True)
        except (binascii.Error, ValueError) as error:
            # 捕获 Base64 解码异常，转换为业务友好的错误信息
            # from error 保留原始异常链，便于调试时追踪根因
            raise PdfExtractionError("PDF Base64 内容无效") from error

        # 第二层防御：文件大小限制（15 MiB），防止超大文件耗尽内存或超时
        if len(raw) > self.MAX_FILE_BYTES:
            raise PdfExtractionError("PDF 不能超过 15 MiB")

        # 第三层防御：魔数（Magic Number）校验
        # 合法 PDF 文件的字节流必须以 "%PDF-" 开头（如 %PDF-1.4）
        # 这一步可以在不依赖 pypdf 的情况下快速拦截伪造文件
        if not raw.startswith(b"%PDF-"):
            raise PdfExtractionError("文件不是有效的 PDF")

        # 使用 pypdf 库将原始字节流包装为可读的 PDF 对象
        # strict=True 启用严格模式：任何格式不规范都会抛出异常，而非静默忽略
        try:
            reader = PdfReader(BytesIO(raw), strict=True)
        except Exception as error:
            # pypdf 的异常类型较多且可能随版本变化，统一捕获为"损坏"错误
            raise PdfExtractionError("PDF 已损坏或格式无法解析") from error

        # 第四层防御：PDF 结构级安全检查
        if reader.is_encrypted:
            # 加密 PDF 需要密码才能提取文本，当前不支持，引导用户手动解密
            raise PdfExtractionError("暂不支持加密 PDF，请先解除密码保护")
        if not reader.pages:
            # 空文档无实际内容，无需继续处理
            raise PdfExtractionError("PDF 不包含页面")
        if len(reader.pages) > self.MAX_PAGES:
            # 页数上限 100 页，避免处理时间过长或内存占用过高
            raise PdfExtractionError("PDF 不能超过 100 页")

        # 初始化结果容器：存储每页提取后的结构化数据
        pages: list[PdfPage] = []
        # 全局字符计数器：实时累加所有页面的文本长度，用于总量控制
        total_chars = 0

        # 逐页遍历 PDF，enumerate 从 1 开始使页码更符合人类阅读习惯（第1页而非第0页）
        for page_number, page in enumerate(reader.pages, start=1):
            try:
                # 调用 pypdf 提取单页原始文本；若返回 None 则替换为空字符串
                # _clean_page_text() 统一换行符、压缩空白、去除空行（见下方静态方法）
                text = self._clean_page_text(page.extract_text() or "")
            except Exception as error:
                # 单页提取失败也必须中断整个流程，防止部分脏数据入库
                # 错误信息包含具体页号，方便用户定位问题
                raise PdfExtractionError(f"第 {page_number} 页文字提取失败") from error

            # 累加本页字符数到全局计数器
            total_chars += len(text)

            # 第五层防御：总文本量上限（500,000 字符）
            # 在循环中实时检查，一旦超标立即中断，避免处理完才发现超出限制
            if total_chars > self.MAX_TEXT_CHARS:
                raise PdfExtractionError("PDF 提取正文不能超过 500000 字符")

            # 将清洗后的单页文本封装为 PdfPage 数据对象，加入结果列表
            pages.append(PdfPage(page_number=page_number, text=text, char_count=len(text)))

        # 计算所有页面的有效字符总数（去除所有空白字符后的纯内容长度）
        # 用于判断该 PDF 是"文字型"还是"扫描件/图片型"
        meaningful_chars = len(re.sub(r"\s+", "", "".join(page.text for page in pages)))

        # 如果有效字符少于阈值（20个），判定为扫描件或图片型 PDF
        # 此时不应该把乱码或零星符号当作有效内容导入知识库
        if meaningful_chars < self.MIN_EXTRACTED_CHARS:
            # 返回特殊状态 OCR_REQUIRED，提示调用方需要走 OCR 流程
            # content 设为空字符串，避免垃圾数据污染下游系统
            return PdfExtractResponse(
                file_name=request.file_name,
                status="OCR_REQUIRED",          # 标记需要光学字符识别
                extraction_mode="SCANNED",      # 提取模式标记为扫描件
                page_count=len(pages),          # 仍返回页数信息供参考
                char_count=total_chars,
                content="",                     # 关键：不返回任何正文内容
                preview="未提取到足够文字，文件可能是扫描件，需要 OCR 后再导入。",
                pages=pages,
            )

        # 正常路径：文字型 PDF，组装带页码标记的完整正文
        # 格式：[PDF_PAGE:1]\n第一页内容\n\n[PDF_PAGE:2]\n第二页内容...
        # 页码标记的意义：后续 RAG 分块时可保留来源位置，回答时可引用"来自原文件第X页"
        content = "\n\n".join(
            f"[PDF_PAGE:{page.page_number}]\n{page.text}"
            for page in pages
            if page.text  # 只拼接有内容的页面，跳过空页
        )

        # 生成预览摘要：取所有有内容页面的前 2000 个字符，用于前端展示
        preview = "\n\n".join(page.text for page in pages if page.text)[:2_000]

        # 返回成功状态的结构化响应对象，包含完整的提取结果
        return PdfExtractResponse(
            file_name=request.file_name,
            status="READY",                    # 标记 PDF 已完成提取，仍需提交并通过人工审核
            extraction_mode="TEXT",            # 提取模式标记为文字型
            page_count=len(pages),
            char_count=total_chars,
            content=content,                   # 带页码标记的完整正文
            preview=preview,                   # 前端预览用的摘要
            pages=pages,                       # 分页明细（含每页独立内容和统计）
        )

    @staticmethod
    def _clean_page_text(text: str) -> str:
        """统一换行并压缩行内空白，保留段落和页内阅读顺序。"""
        normalized = text.replace("\r\n", "\n").replace("\r", "\n").replace("\u00a0", " ")
        lines = [re.sub(r"[ \t]+", " ", line).strip() for line in normalized.split("\n")]
        return "\n".join(line for line in lines if line).strip()
