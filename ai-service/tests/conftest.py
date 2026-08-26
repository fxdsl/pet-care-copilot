"""隔离开发机密钥，保证自动化测试不会意外请求收费的外部模型。"""

from __future__ import annotations

import os
import hashlib
import math

import pytest

# pytest 启动后只修改当前测试进程环境，不会删除 Windows 用户或系统环境变量。
# 空字符串会覆盖项目 .env 中可能存在的 Key，避免测试误用真实免费额度。
os.environ["DASHSCOPE_API_KEY"] = ""
os.environ["AI_LLM_API_KEY"] = ""

# 必须先隔离密钥再导入应用，否则配置缓存可能提前读取真实环境变量。
from app.api.routes import get_embedding_service  # noqa: E402
from app.main import app  # noqa: E402


class FakeEmbeddingService:
    """测试专用确定性向量，避免单元测试下载或加载真实 ONNX 模型。"""

    model_name = "BAAI/bge-small-zh-v1.5"
    dimensions = 64

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        """批量生成测试向量。"""
        return [self._embed(text) for text in texts]

    def embed_document(self, text: str) -> list[float]:
        """测试中问题和文档使用同一稳定映射。"""
        return self._embed(text)

    def embed_query(self, text: str) -> list[float]:
        """生成与文档维度一致的测试问题向量。"""
        return self._embed(text)

    def _embed(self, text: str) -> list[float]:
        """把 Unicode 字符映射到小向量；该实现不会进入生产代码。"""
        vector = [0.0] * self.dimensions
        for token in text.lower().replace(" ", ""):
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            vector[int.from_bytes(digest[:2], "big") % self.dimensions] += 1.0
        norm = math.sqrt(sum(value * value for value in vector)) or 1.0
        return [value / norm for value in vector]

    @staticmethod
    def cosine_similarity(left: list[float], right: list[float]) -> float:
        """计算单元测试向量的余弦相似度。"""
        if not left or len(left) != len(right):
            return -1.0
        return sum(a * b for a, b in zip(left, right, strict=True))


@pytest.fixture(autouse=True)
def use_fake_embedding() -> FakeEmbeddingService:
    """所有 API 测试自动覆盖模型依赖，结束后清理覆盖状态。"""
    fake = FakeEmbeddingService()
    app.dependency_overrides[get_embedding_service] = lambda: fake
    yield fake
    app.dependency_overrides.clear()
