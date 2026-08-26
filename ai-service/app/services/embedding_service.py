"""基于 FastEmbed/ONNX 的免费本地中文语义向量服务。"""

from __future__ import annotations

import math
import threading
from pathlib import Path
from typing import Protocol

from fastembed import TextEmbedding


class EmbeddingProvider(Protocol):
    """知识预处理与检索共同依赖的最小向量接口，便于隔离自动化测试。"""

    model_name: str
    dimensions: int

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        """批量生成知识正文向量。"""

    def embed_document(self, text: str) -> list[float]:
        """生成知识正文向量。"""

    def embed_query(self, text: str) -> list[float]:
        """生成用户问题向量。"""

    def cosine_similarity(self, left: list[float], right: list[float]) -> float:
        """计算两个向量的余弦相似度。"""


class FastEmbedEmbeddingService:
    """延迟加载中文 BGE 模型，首次使用时下载，之后从本地缓存读取。"""

    def __init__(self, model_name: str, dimensions: int, cache_dir: str) -> None:
        self.model_name = model_name
        self.dimensions = dimensions
        self.cache_dir = Path(cache_dir)
        self._model: TextEmbedding | None = None
        self._lock = threading.Lock()

    @property
    def model(self) -> TextEmbedding:
        """线程安全地初始化 ONNX 模型，避免启动阶段无条件占用内存。"""
        if self._model is None:
            with self._lock:
                if self._model is None:
                    self.cache_dir.mkdir(parents=True, exist_ok=True)
                    # FastEmbed 的 URL 回退下载会生成 fast-<模型名> 目录；发现后显式离线加载，
                    # 避免每次启动仍先访问 Hugging Face，也避免无网络环境启动失败。
                    extracted_model = self.cache_dir / f"fast-{self.model_name.rsplit('/', 1)[-1]}"
                    model_options = (
                        {"specific_model_path": str(extracted_model)}
                        if extracted_model.is_dir()
                        else {}
                    )
                    self._model = TextEmbedding(
                        model_name=self.model_name,
                        cache_dir=str(self.cache_dir),
                        **model_options,
                    )
        return self._model

    def embed_document(self, text: str) -> list[float]:
        """生成单条知识向量。"""
        return self.embed_documents([text])[0]

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        """在一次 ONNX 批处理中生成多个 passage 向量，减少重建知识库耗时。"""
        return [self._normalize(vector.tolist()) for vector in self.model.passage_embed(texts)]

    def embed_query(self, text: str) -> list[float]:
        """使用 query 模式生成问题向量，使 BGE 的查询前缀策略生效。"""
        vector = next(iter(self.model.query_embed([text])))
        return self._normalize(vector.tolist())

    def _normalize(self, values: list[float]) -> list[float]:
        """校验维度并压缩 JSON 小数长度；BGE 输出本身已归一化。"""
        if len(values) != self.dimensions:
            raise ValueError(
                f"embedding dimension mismatch: expected {self.dimensions}, got {len(values)}"
            )
        return [round(float(value), 8) for value in values]

    @staticmethod
    def cosine_similarity(left: list[float], right: list[float]) -> float:
        """拒绝空向量和维度不一致的数据，防止旧模型向量参与排序。"""
        if not left or len(left) != len(right):
            return -1.0
        left_norm = math.sqrt(sum(value * value for value in left))
        right_norm = math.sqrt(sum(value * value for value in right))
        if left_norm == 0.0 or right_norm == 0.0:
            return -1.0
        return sum(a * b for a, b in zip(left, right, strict=True)) / (
            left_norm * right_norm
        )
