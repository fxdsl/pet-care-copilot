"""通过 Spring Boot 管理端入口提交项目内置的 UTF-8 知识样例。"""

from __future__ import annotations

import argparse
from pathlib import Path

import httpx


def main() -> None:
    """读取管理员凭证、加载幼猫样例并创建待预检的知识投稿。"""
    parser = argparse.ArgumentParser()
    parser.add_argument("--business-url", default="http://localhost:8080")
    parser.add_argument("--access-token", required=True)
    args = parser.parse_args()

    sample_path = Path(__file__).resolve().parents[1] / "knowledge_samples" / "幼猫基础喂养.md"
    content = sample_path.read_text(encoding="utf-8")
    response = httpx.post(
        f"{args.business_url}/api/v1/admin/knowledge-submissions/uploads",
        headers={"Authorization": f"Bearer {args.access_token}"},
        json={
            "title": "幼猫基础喂养",
            "sourceName": "项目开发样例",
            "sourceAuthor": "宠里个宠项目组",
            "sourceUrl": None,
            "fileName": "幼猫基础喂养.md",
            "documentType": "MARKDOWN",
            "petType": "CAT",
            "category": "FEEDING",
            "content": content,
            "sourcePublishedAt": None,
            "expiresAt": None,
        },
        timeout=20,
    )
    if not response.is_success:
        raise RuntimeError(f"知识投稿失败（{response.status_code}）：{response.text}")
    submission = response.json()
    print(
        "投稿已创建，请在管理员知识库页面完成审核："
        f"id={submission['id']}, status={submission['status']}"
    )


if __name__ == "__main__":
    main()
