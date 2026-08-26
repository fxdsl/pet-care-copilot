"""运行固定中文检索评测集并输出 Top-1/Top-3 指标。"""

from __future__ import annotations

import json
import sys
from pathlib import Path

# Windows 终端默认代码页可能不是 UTF-8，显式统一编码以正确显示中文问题。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

from app.config import get_settings  # noqa: E402
from app.services.embedding_service import FastEmbedEmbeddingService  # noqa: E402


def main() -> None:
    """加载固定语料、批量生成向量并验证期望文档排名。"""
    dataset_path = PROJECT_ROOT / "evaluation" / "retrieval_cases.json"
    dataset = json.loads(dataset_path.read_text(encoding="utf-8"))
    settings = get_settings()
    service = FastEmbedEmbeddingService(
        settings.embedding_model,
        settings.embedding_dimensions,
        settings.embedding_cache_dir,
    )
    documents = dataset["documents"]
    document_vectors = {
        document["id"]: service.embed_document(document["text"])
        for document in documents
    }

    top1_hits = 0
    top3_hits = 0
    for case in dataset["cases"]:
        query_vector = service.embed_query(case["question"])
        ranked = sorted(
            (
                (document_id, service.cosine_similarity(query_vector, vector))
                for document_id, vector in document_vectors.items()
            ),
            key=lambda item: item[1],
            reverse=True,
        )
        expected = case["expected_document_id"]
        top1_hits += int(ranked[0][0] == expected)
        top3_hits += int(expected in {item[0] for item in ranked[:3]})
        print(
            f"question={case['question']} expected={expected} "
            f"top1={ranked[0][0]} score={ranked[0][1]:.4f}"
        )

    total = len(dataset["cases"])
    top1 = top1_hits / total
    top3 = top3_hits / total
    print(f"model={settings.embedding_model} cases={total} top1={top1:.2%} top3={top3:.2%}")
    if top1 < 0.8:
        raise SystemExit("Top-1 accuracy below required 80%")


if __name__ == "__main__":
    main()
