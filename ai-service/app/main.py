from fastapi import FastAPI

from app.api.routes import agent_router, knowledge_router, router
from app.config import get_settings

settings = get_settings()

# 应用对象只负责装配路由；业务算法分别位于 services 目录。
app = FastAPI(
    title="宠里个宠 AI Service",
    description="AI inference boundary for the pet assistant platform.",
    version=settings.app_version,
)

app.include_router(router)
app.include_router(knowledge_router)
app.include_router(agent_router)
