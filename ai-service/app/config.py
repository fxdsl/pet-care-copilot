from functools import lru_cache

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """从环境变量加载 AI 服务配置，敏感 Key 只允许存在于本机 .env。"""

    app_name: str = "pet-assistant-ai-service"
    app_version: str = "0.12.0"
    environment: str = "local"
    # 中文 BGE 通过 ONNX 在本机运行，不调用收费 embedding API。
    embedding_model: str = "BAAI/bge-small-zh-v1.5"
    embedding_dimensions: int = 512
    embedding_cache_dir: str = ".cache/fastembed"
    # 优先兼容项目原有变量，同时直接识别阿里云百炼官方推荐的变量名。
    llm_api_key: str | None = Field(
        default=None,
        validation_alias=AliasChoices("AI_LLM_API_KEY", "DASHSCOPE_API_KEY"),
    )
    # 默认使用华北 2（北京）共享兼容地址；其他地域可通过环境变量覆盖。
    llm_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    llm_chat_model: str = "qwen3.7-plus"
    llm_timeout_seconds: float = 20.0
    # Agent 循环采用三层保险：业务工具上限、连续异常上限和 LangGraph 递归上限。
    agent_max_tool_calls: int = 3
    agent_max_consecutive_errors: int = 2
    agent_recursion_limit: int = 12
    agent_max_observation_chars: int = 5_000

    model_config = SettingsConfigDict(
        env_prefix="AI_",
        env_file=".env",
        extra="ignore",
    )

    @property
    def llm_enabled(self) -> bool:
        """只有 Key、地址和模型名同时存在时才启用兼容大模型调用。"""
        return bool(self.llm_api_key and self.llm_base_url and self.llm_chat_model)

    @property
    def answer_mode(self) -> str:
        """返回健康接口可展示的回答模式，便于判断当前是否使用外部模型。"""
        return "agent-compatible-llm" if self.llm_enabled else "agent-local-extractive"

    @property
    def is_bailian_compatible(self) -> bool:
        """判断当前地址是否为阿里云百炼兼容端点，用于追加供应商专属参数。"""
        return "aliyuncs.com" in self.llm_base_url.lower()

    @property
    def llm_provider(self) -> str:
        """返回不含任何密钥信息的供应商标识，供健康检查确认配置。"""
        if not self.llm_enabled:
            return "disabled"
        return "aliyun-bailian" if self.is_bailian_compatible else "openai-compatible"


@lru_cache
def get_settings() -> Settings:
    """缓存配置对象，避免每次请求重复读取 .env。"""
    return Settings()
