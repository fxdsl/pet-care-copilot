"""第六周基于 LangGraph 的受控 ReAct 宠物问答 Agent。"""

from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from typing import Literal, TypedDict

import httpx
from langgraph.errors import GraphRecursionError
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.config import Settings
from app.models import (
    AgentCandidate,
    AgentRequest,
    AgentResponse,
    AgentSource,
    AgentStep,
    PetProfileContext,
)
from app.services.embedding_service import EmbeddingProvider, FastEmbedEmbeddingService

logger = logging.getLogger(__name__)


class AgentDecision(BaseModel):
    """模型只返回动作或最终答案，不要求也不保存隐藏推理过程。"""

    action: Literal["tool", "final"]
    tool_name: str | None = None
    tool_arguments: dict[str, object] = Field(default_factory=dict)
    answer: str | None = None

    @model_validator(mode="after")
    def validate_action_payload(self) -> "AgentDecision":
        """工具动作必须提供名称，最终动作必须提供非空答案。"""
        if self.action == "tool" and not self.tool_name:
            raise ValueError("tool action requires tool_name")
        if self.action == "final" and not (self.answer or "").strip():
            raise ValueError("final action requires answer")
        return self


class SearchKnowledgeArguments(BaseModel):
    """知识检索工具参数，拒绝模型附加的未知字段。"""

    model_config = ConfigDict(extra="forbid")

    query: str = Field(min_length=1, max_length=500)
    top_k: int = Field(default=3, ge=1, le=3)


class ReadProfileArguments(BaseModel):
    """档案工具不接收参数，防止模型尝试读取其他档案。"""

    model_config = ConfigDict(extra="forbid")


class AgentGraphState(TypedDict, total=False):
    """LangGraph 节点之间共享的显式状态。"""
    #展示当前Agent的状态，包括请求、观察、来源、步骤、工具签名、工具调用次数、连续错误次数、决策和答案
    request: AgentRequest
    observations: list[str]
    sources: list[AgentSource]
    steps: list[AgentStep]
    tool_signatures: list[str]
    tool_call_count: int
    consecutive_tool_errors: int
    decision: AgentDecision | None
    answer: str
    stage: str
    model_name: str | None
    routing_reason: str
    termination_reason: str
    guard_message: str
    max_score: float | None


@dataclass(frozen=True)
class ToolExecutionResult:
    """工具内部结果；完整观察只给下一轮模型，不直接展示给网页。"""

    observation: str
    summary: str
    sources: list[AgentSource]
    max_score: float | None = None
    top_content: str | None = None


class AgentToolError(ValueError):
    """表示工具名称、参数或执行结果不符合受控边界。"""


class PetAgentService:
    """运行受控 ReAct 循环，并对工具次数、重复调用和异常实施熔断。"""

    _sentence_boundary = re.compile(r"(?<=[。！？!?])|\n+")
    _lexical_token = re.compile(r"[\u3400-\u9fff]|[a-z0-9]+")
    _tool_names = frozenset({"search_pet_knowledge", "read_pet_profile"})

    def __init__(
        self,
        settings: Settings,
        embedding_service: EmbeddingProvider | None = None,
    ) -> None:
        self.settings = settings
        self.embedding_service = embedding_service or FastEmbedEmbeddingService(
            model_name=settings.embedding_model,
            dimensions=settings.embedding_dimensions,
            cache_dir=settings.embedding_cache_dir,
        )
        self.graph = self._build_graph()

    def answer(self, request: AgentRequest) -> AgentResponse:
        """运行 LangGraph；无 Key 时使用同一知识工具做免费本地降级。"""
        if not self.settings.llm_enabled:
            response = self._answer_locally(request)
            self._log_route(response)
            return response

        initial_state: AgentGraphState = {
            "request": request,
            "observations": [],
            "sources": [],
            "steps": [],
            "tool_signatures": [],
            "tool_call_count": 0,
            "consecutive_tool_errors": 0,
            "decision": None,
            "max_score": None,
        }
        try:
            final_state = self.graph.invoke(
                initial_state,
                {"recursion_limit": self.settings.agent_recursion_limit},
            )
            response = self._to_response(final_state)
        except GraphRecursionError:
            response = self._recursion_limit_response(request, initial_state)
        self._log_route(response)
        return response

    def _build_graph(self):
        """装配 reason → tool → reason 循环和两个终止节点。"""
        builder = StateGraph(AgentGraphState)
        #添加节点，分别为reason、tool、finish、guard，分别对应模型的决策、工具调用、最终答案、异常处理
        builder.add_node("reason", self._reason_node)
        builder.add_node("tool", self._tool_node)
        builder.add_node("finish", self._finish_node)
        builder.add_node("guard", self._guard_node)
        #添加边，START表示从reason节点开始。
        builder.add_edge(START, "reason")
        #动态跳转，根据模型的决策选择tool、finish或guard节点
        builder.add_conditional_edges(
            "reason",
            self._route_after_reason,
            {"tool": "tool", "finish": "finish", "guard": "guard"},
        )
        #添加边，tool节点根据模型的决策选择reason节点或guard节点
        builder.add_conditional_edges(
            "tool",
            self._route_after_tool,
            {"reason": "reason", "guard": "guard"},
        )
        #添加边，finish表示终结节点，根据模型的决策选择reason节点或guard节点
        builder.add_edge("finish", END)
        builder.add_edge("guard", END)
        #返回构建的LangGraph图
        return builder.compile()

    def _reason_node(self, state: AgentGraphState) -> dict[str, object]:
        """请求模型选择白名单工具或提交最终答案。"""
        if state.get("tool_call_count", 0) >= self.settings.agent_max_tool_calls:
            return {
                "termination_reason": "TOOL_CALL_LIMIT",
                "guard_message": "工具调用已达到本轮上限。",
            }
        try:
            decision = self._call_agent_model(state)
        except Exception as error:  # noqa: BLE001 - 模型和 JSON 错误统一进入安全终止
            logger.warning("Agent model decision failed: %s", type(error).__name__)
            return {
                "termination_reason": "MODEL_DECISION_ERROR",
                "guard_message": "模型决策暂时不可用。",
                "steps": self._append_step(
                    state,
                    node="reason",
                    action="MODEL_DECISION",
                    status="ERROR",
                    summary="模型决策失败，已停止本轮工具循环。",
                ),
            }

        if decision.action == "tool":
            summary = f"模型请求工具 {decision.tool_name}，等待参数校验。"
        else:
            summary = "模型提交最终回答，进入完成节点。"
        return {
            "decision": decision,
            "steps": self._append_step(
                state,
                node="reason",
                action="MODEL_DECISION",
                tool_name=decision.tool_name,
                status="SUCCESS",
                summary=summary,
            ),
        }

    def _route_after_reason(self, state: AgentGraphState) -> Literal["tool", "finish", "guard"]:
        """根据显式决策路由，不同时配置普通边以免节点重复执行。"""
        # 检查是否有终止原因，如果有则直接跳转到guard节点，避免重复执行
        if state.get("termination_reason"):
            return "guard"
        decision = state.get("decision")
        # 如果没有有效决策跳转到guard节点，避免重复执行
        if decision is None:
            return "guard"
        if decision.action == "final":
            return "finish"
        if state.get("tool_call_count", 0) >= self.settings.agent_max_tool_calls:
            return "guard"
        return "tool"

    def _tool_node(self, state: AgentGraphState) -> dict[str, object]:
        """验证工具白名单和参数，执行一次工具并生成有界观察结果。"""
        #检查决策是否合法
        decision = state.get("decision")
        #如果没有对应的工具名称或参数，直接返回错误
        if decision is None or decision.action != "tool" or not decision.tool_name:
            return {
                "termination_reason": "INVALID_TOOL_DECISION",
                "guard_message": "模型没有提供有效工具动作。",
            }
        # 增加工具调用次数
        attempt_count = state.get("tool_call_count", 0) + 1
        # 检查工具调用是否重复，避免循环调用
        signature = self._tool_signature(decision.tool_name, decision.tool_arguments)
        signatures = list(state.get("tool_signatures", []))
        if signature in signatures:
            return {
                "tool_call_count": attempt_count,
                "termination_reason": "REPEATED_TOOL_CALL",
                "guard_message": "检测到完全相同的工具调用。",
                "steps": self._append_step(
                    state,
                    node="tool",
                    action="EXECUTE_TOOL",
                    tool_name=decision.tool_name,
                    status="BLOCKED",
                    summary="重复工具调用已被拦截，避免循环和 Token 浪费。",
                ),
            }
        signatures.append(signature)

        try:
            # 执行工具，获取结果并更新状态
            result = self._execute_tool(
                decision.tool_name,
                decision.tool_arguments,
                state["request"],
            )
        except Exception as error:  # noqa: BLE001 - 所有工具错误都进入受控观察或熔断
            error_count = state.get("consecutive_tool_errors", 0) + 1
            terminal = error_count >= self.settings.agent_max_consecutive_errors
            logger.warning(
                "Agent tool failed name=%s error=%s count=%s",
                decision.tool_name,
                type(error).__name__,
                error_count,
            )
            update: dict[str, object] = {
                "tool_call_count": attempt_count,
                "tool_signatures": signatures,
                "consecutive_tool_errors": error_count,
                "observations": [
                    *state.get("observations", []),
                    f"TOOL_ERROR name={decision.tool_name}; 请修正参数或直接回答。",
                ],
                "steps": self._append_step(
                    state,
                    node="tool",
                    action="EXECUTE_TOOL",
                    tool_name=decision.tool_name,
                    status="ERROR",
                    summary="工具参数或执行失败，未向页面暴露内部异常。",
                ),
            }
            if terminal:
                update.update({
                    "termination_reason": "TOOL_ERROR_LIMIT",
                    "guard_message": "连续工具异常达到熔断阈值。",
                })
            return update

        return {
            "tool_call_count": attempt_count,
            "tool_signatures": signatures,
            "consecutive_tool_errors": 0,
            "observations": [*state.get("observations", []), result.observation],
            "sources": self._merge_sources(state.get("sources", []), result.sources),
            "max_score": self._max_score(state.get("max_score"), result.max_score),
            "steps": self._append_step(
                state,
                node="tool",
                action="EXECUTE_TOOL",
                tool_name=decision.tool_name,
                status="SUCCESS",
                summary=result.summary,
            ),
        }

    @staticmethod
    def _route_after_tool(state: AgentGraphState) -> Literal["reason", "guard"]:
        """工具成功或可恢复错误回到模型，触发熔断时进入 guard。"""
        return "guard" if state.get("termination_reason") else "reason"

    def _finish_node(self, state: AgentGraphState) -> dict[str, object]:
        """把模型最终答案和真实工具来源组装为正常结束状态。"""
        decision = state.get("decision")
        #从最后一次模型决策中取得答案
        answer = (decision.answer if decision else "") or ""
        sources = state.get("sources", [])
        with_knowledge = bool(sources)
        return {
            "answer": answer.strip(),
            "stage": "week-6-agent-rag" if with_knowledge else "week-6-agent-general",
            "model_name": self.settings.llm_chat_model,
            "routing_reason": (
                "AGENT_FINAL_WITH_KNOWLEDGE" if with_knowledge else "AGENT_FINAL_GENERAL"
            ),
            "termination_reason": "COMPLETED",
            "steps": self._append_step(
                state,
                node="finish",
                action="FINAL_ANSWER",
                status="SUCCESS",
                summary=(
                    f"回答完成，并返回 {len(sources)} 条可核对来源。"
                    if with_knowledge
                    else "回答完成；本轮没有采用本地知识来源。"
                ),
            ),
        }

    def _guard_node(self, state: AgentGraphState) -> dict[str, object]:
        """以可解释的安全提示结束异常、重复或超限循环。"""
        reason = state.get("termination_reason") or "INVALID_AGENT_STATE"
        messages = {
            "TOOL_CALL_LIMIT": "为避免工具循环，本轮已停止继续检索。请补充宠物类型、年龄、体重或更具体的问题后重试。",
            "REPEATED_TOOL_CALL": "检测到重复工具调用，本轮已安全终止。请换一种问法或补充更具体的宠物信息。",
            "TOOL_ERROR_LIMIT": "工具连续执行失败，本轮已触发熔断。请稍后重试，或检查知识向量和请求参数。",
            "MODEL_DECISION_ERROR": "模型暂时无法完成 Agent 决策，请稍后重试；紧急健康问题请直接联系专业兽医。",
            "INVALID_TOOL_DECISION": "Agent 返回了无效工具动作，本轮已安全终止，请重新提问。",
            "INVALID_AGENT_STATE": "Agent 状态异常，本轮已安全终止，请稍后重试。",
        }
        return {
            "answer": messages.get(reason, messages["INVALID_AGENT_STATE"]),
            "stage": "week-6-agent-guarded",
            "model_name": self.settings.llm_chat_model,
            "routing_reason": reason,
            "termination_reason": reason,
            "steps": self._append_step(
                state,
                node="guard",
                action="STOP_AGENT",
                status="BLOCKED",
                summary=state.get("guard_message", "Agent 已按安全策略终止。"),
            ),
        }

    def _execute_tool(
        self,
        name: str,
        arguments: dict[str, object],
        request: AgentRequest,
    ) -> ToolExecutionResult:
        """ 执行工具，只分派两个进程内只读工具，未知名称一律拒绝。"""
        if name not in self._tool_names:
            raise AgentToolError("tool is not in allowlist")
        if name == "search_pet_knowledge":
            parsed = SearchKnowledgeArguments.model_validate(arguments)
            return self._search_pet_knowledge(parsed, request)
        ReadProfileArguments.model_validate(arguments)
        return self._read_pet_profile(request.pet_profile)

    def _search_pet_knowledge(
        self,
        arguments: SearchKnowledgeArguments,
        request: AgentRequest,
    ) -> ToolExecutionResult:
        """这就相当于查找本地候选知识的工具，一切对本地知识的查找都可以调用这个工具，
        对 Java 已授权候选执行 BGE 检索，并限制观察正文总长度。"""
        query_vector = self.embedding_service.embed_query(arguments.query.strip())
        ranked: list[tuple[AgentCandidate, float]] = []
        for candidate in request.candidates:
            if candidate.embedding_model != self.embedding_service.model_name:
                continue
            score = self.embedding_service.cosine_similarity(query_vector, candidate.embedding)
            ranked.append((candidate, score))
        ranked.sort(key=lambda item: item[1], reverse=True)
        max_score = ranked[0][1] if ranked else None
        limit = min(arguments.top_k, request.top_k)
        selected = [item for item in ranked if item[1] >= request.min_score][:limit]
        if not selected:
            highest = "none" if max_score is None else f"{max_score:.4f}"
            return ToolExecutionResult(
                observation=f"KNOWLEDGE_NOT_FOUND highest_score={highest}",
                summary="知识检索没有找到达到阈值的资料。",
                sources=[],
                max_score=max_score,
            )

        sections: list[str] = []
        sources: list[AgentSource] = []
        for index, (candidate, score) in enumerate(selected, start=1):
            sections.append(
                f"[资料{index}｜{candidate.title}｜相关度{score:.2%}]\n"
                f"{candidate.content[:1_500]}"
            )
            sources.append(self._source(candidate, score))
        observation = "KNOWLEDGE_FOUND\n" + "\n\n".join(sections)
        observation = observation[: self.settings.agent_max_observation_chars]
        return ToolExecutionResult(
            observation=observation,
            summary=f"知识检索命中 {len(selected)} 条，最高相关度 {selected[0][1]:.0%}。",
            sources=sources,
            max_score=max_score,
            top_content=selected[0][0].content,
        )

    @staticmethod
    def _read_pet_profile(profile: PetProfileContext | None) -> ToolExecutionResult:
        """只返回本轮请求已携带的档案，不接受任意 ID 或数据库查询。"""
        if profile is None:
            return ToolExecutionResult(
                observation="PROFILE_NOT_SELECTED",
                summary="当前没有选择宠物档案。",
                sources=[],
            )
        safe_profile = profile.model_dump(exclude_none=True)
        return ToolExecutionResult(
            observation="PROFILE_CONTEXT " + json.dumps(
                safe_profile, ensure_ascii=False, separators=(",", ":")
            ),
            summary="已读取本轮用户明确选择的宠物档案。",
            sources=[],
        )

    def _call_agent_model(self, state: AgentGraphState) -> AgentDecision:
        """调用百炼 JSON Object 模式获取 Agent 决策，并把有界工具观察加入下一轮。

        这是 Agent 的"大脑"节点，负责：
        1. 构建结构化 prompt（system + history + current context）
        2. 调用 LLM API 获取 JSON 格式的决策
        3. 验证并返回强类型的决策对象

        Args:
            state: 当前图状态，包含请求、观察历史、工具调用次数等

        Returns:
            AgentDecision: 经过 Pydantic 验证的结构化决策对象

        Raises:
            ValueError: LLM 返回空内容或无法解析为有效 JSON
            ValidationError: JSON 结构不符合 AgentDecision schema
        """

        # 从状态中提取当前请求
        request = state["request"]

        # 步骤1：截取最近的工具观察结果（滑动窗口）
        # 只保留最近 N 条观察，防止上下文过长导致 token 爆炸
        # 例如：max_tool_calls=5 时，只取最后5条工具返回结果
        observations = state.get("observations", [])[-self.settings.agent_max_tool_calls:]

        # 步骤2：定义可用工具的 Schema（告诉模型它能用什么）
        # 这是白名单机制的一部分，模型只能看到这两个工具
        tool_schema = (
            "可用工具：\n"
            "1. search_pet_knowledge(query:string, top_k:1..3)：检索本地宠物知识；"
            "事实型养护问题应优先调用一次。\n"
            "2. read_pet_profile()：只读取用户本轮已选择的宠物档案；没有档案时返回未选择。"
        )

        # 步骤3：构建 System Prompt（定义角色、规则和输出格式）
        # System prompt 是 Agent 行为的核心控制点，包含以下关键要素：
        system = (
            # 角色定义：明确身份和行为约束
            "你是宠物养护 ReAct Agent。不要输出思维过程，只返回一个 JSON 对象。"

            # 输出格式规范：强制 JSON 结构，便于程序化解析
            # 工具调用格式示例
            "需要工具时返回 {\"action\":\"tool\",\"tool_name\":\"工具名\","
            "\"tool_arguments\":{...},\"answer\":null}；"

            # 最终答案格式示例
            "可以回答时返回 {\"action\":\"final\",\"tool_name\":null,"
            "\"tool_arguments\":{},\"answer\":\"最终答案\"}。"

            # 安全约束：防止模型越权或产生幻觉
            "不得调用列表之外的工具，不得重复相同工具和参数。"

            # 回答质量要求：长度、结构、完整性
            "最终回答应直接、详细、分点，通常为 200～500 个中文字符；"
            "缺少体重、月龄、食物热量或症状信息时明确列出需要补充的内容。"

            # 知识使用策略：区分有依据的事实 vs 通用建议
            "知识工具命中时只依据工具资料陈述事实；未命中时可提供标明边界的通用知识。"

            # 医疗安全边界：避免法律风险和误导用户
            "不能替代兽医诊断，呼吸困难、持续抽搐、严重出血等情况建议立即就医。"

            # 追加工具定义（上面定义的 tool_schema 变量）
            + tool_schema
        )

        # 步骤4：构建 User Context（当前轮次的动态信息）
        # 这部分内容每轮都不同，反映当前执行状态
        user_content = (
            f"当前问题：{request.question}\n"  # 用户本轮的实际问题
            f"是否已选择档案：{'是' if request.pet_profile else '否'}\n"  # 个性化上下文
            f"工具调用次数：{state.get('tool_call_count', 0)}/{self.settings.agent_max_tool_calls}\n"  # 资源预算感知
            "已有工具观察：\n"
            + ("\n\n".join(observations) if observations else "无")  # 历史工具结果
        )

        # 步骤5：组装完整的消息列表（遵循 OpenAI 兼容格式）
        messages: list[dict[str, str]] = [
            {"role": "system", "content": system}  # 第一条必须是 system 消息
        ]

        # 步骤6：追加对话历史（提供上下文连贯性）
        # 只取最近12轮对话，防止超出上下文窗口
        # 每条消息截断到4000字符，控制 token 消耗
        messages.extend(
            {
                # 角色映射：内部 USER/ASSISTANT → API 格式 user/assistant
                "role": "user" if item.role == "USER" else "assistant",
                "content": item.content[-4_000:],  # 截断长消息
            }
            for item in request.history[-12:]  # 滑动窗口：最近12轮
        )

        # 步骤7：追加当前轮次的用户消息（包含问题和工具观察）
        messages.append({"role": "user", "content": user_content})

        # 步骤8：调用 LLM API（使用兼容 OpenAI 的接口）
        # 内部会处理 HTTP 请求、超时、错误重试等
        content = self._call_compatible_llm(messages)

        # 步骤9：解析并验证返回结果
        # _strip_json_fence(): 处理可能的 Markdown 代码块包裹
        # model_validate_json(): Pydantic 自动校验 JSON 结构和字段类型
        # 如果格式不匹配会抛出 ValidationError，被上层捕获后进入 guard 节点
        return AgentDecision.model_validate_json(self._strip_json_fence(content))

    def _call_compatible_llm(self, messages: list[dict[str, str]]) -> str:
        """使用现有百炼兼容配置调用一次结构化 Agent 决策。"""
        payload: dict[str, object] = {
            "model": self.settings.llm_chat_model,
            "temperature": 0.2,
            "max_tokens": 1_200,
            "messages": messages,
            "response_format": {"type": "json_object"},
        }
        if self.settings.is_bailian_compatible:
            payload["enable_thinking"] = False
        response = httpx.post(
            f"{self.settings.llm_base_url.rstrip('/')}/chat/completions",
            headers={"Authorization": f"Bearer {self.settings.llm_api_key}"},
            json=payload,
            timeout=self.settings.llm_timeout_seconds,
        )
        response.raise_for_status()
        content = response.json()["choices"][0]["message"]["content"].strip()
        if not content:
            raise ValueError("compatible model returned empty content")
        return content

    def _answer_locally(self, request: AgentRequest) -> AgentResponse:
        """未配置 Key 时仍使用知识检索工具，保持免费、可核对的降级回答。"""

        result = self._search_pet_knowledge(
            SearchKnowledgeArguments(query=request.question, top_k=min(request.top_k, 3)),
            request,
        )
        step = AgentStep(
            sequence=1,
            node="fallback",
            action="LOCAL_KNOWLEDGE_SEARCH",
            tool_name="search_pet_knowledge",
            status="SUCCESS",
            summary=result.summary,
        )
        if result.top_content:
            answer = self._extractive_answer(request.question, result.top_content)
            stage = "week-6-agent-local"
            reason = "LLM_DISABLED_LOCAL_KNOWLEDGE"
        else:
            answer = (
                "当前没有配置通用模型，且本地知识工具未找到足够相关的资料。"
                "请补充宠物类型、月龄、体重、食物品牌或症状持续时间后重试；"
                "紧急健康情况请立即联系专业兽医。"
            )
            stage = "week-6-agent-local-no-context"
            reason = "LLM_DISABLED_NO_KNOWLEDGE"
        return AgentResponse(
            answer=answer,
            conversation_id=request.conversation_id,
            sources=result.sources,
            stage=stage,
            model_name="local-extractive" if result.top_content else None,
            routing_reason=reason,
            max_score=self._rounded(result.max_score),
            agent_steps=[step],
            termination_reason="LLM_DISABLED_LOCAL_FALLBACK",
            tool_call_count=1,
        )

    def _extractive_answer(self, question: str, content: str) -> str:
        """从最高分知识分块提取最相关的相邻句子。"""
        sentences = self._meaningful_sentences(content)
        if not sentences:
            return "知识工具找到了资料，但正文格式无法解析，请检查知识文档。"
        question_tokens = set(self._lexical_token.findall(question.lower()))
        scores = [
            len(question_tokens.intersection(self._lexical_token.findall(sentence.lower())))
            for sentence in sentences
        ]
        best = max(range(len(sentences)), key=scores.__getitem__)
        excerpt = "".join(sentences[max(0, best - 1):best + 2])[:800]
        return (
            f"根据本地知识资料：{excerpt}\n\n"
            "以上内容用于日常养护参考，不能替代专业兽医诊断。"
        )

    def _meaningful_sentences(self, content: str) -> list[str]:
        """跳过 Markdown 标题和引用后切成可读句子。"""
        lines = [
            line.strip()
            for line in content.splitlines()
            if line.strip() and not line.strip().startswith(("#", ">"))
        ]
        return [
            item.strip()
            for item in self._sentence_boundary.split("\n".join(lines))
            if item.strip()
        ]

    def _to_response(self, state: AgentGraphState) -> AgentResponse:
        """把图最终状态转换成稳定的外部响应模型。"""
        return AgentResponse(
            answer=state["answer"],
            conversation_id=state["request"].conversation_id,
            sources=state.get("sources", []),
            stage=state["stage"],
            model_name=state.get("model_name"),
            routing_reason=state["routing_reason"],
            max_score=self._rounded(state.get("max_score")),
            agent_steps=state.get("steps", []),
            termination_reason=state["termination_reason"],
            tool_call_count=state.get("tool_call_count", 0),
        )

    def _recursion_limit_response(
        self,
        request: AgentRequest,
        state: AgentGraphState,
    ) -> AgentResponse:
        """LangGraph 最后保险触发时返回稳定响应而不是 500。"""
        step = AgentStep(
            sequence=len(state.get("steps", [])) + 1,
            node="guard",
            action="STOP_AGENT",
            status="BLOCKED",
            summary="LangGraph 递归上限触发，已强制终止本轮。",
        )
        return AgentResponse(
            answer="Agent 执行步骤超过安全上限，本轮已终止。请简化问题后重试。",
            conversation_id=request.conversation_id,
            sources=state.get("sources", []),
            stage="week-6-agent-guarded",
            model_name=self.settings.llm_chat_model,
            routing_reason="GRAPH_RECURSION_LIMIT",
            max_score=self._rounded(state.get("max_score")),
            agent_steps=[*state.get("steps", []), step],
            termination_reason="GRAPH_RECURSION_LIMIT",
            tool_call_count=state.get("tool_call_count", 0),
        )

    @staticmethod
    def _source(candidate: AgentCandidate, score: float) -> AgentSource:
        """把候选元数据转换为不可由模型伪造的来源。"""
        return AgentSource(
            title=candidate.title,
            url=candidate.source_url,
            chunk_id=candidate.chunk_id,
            score=round(score, 4),
            file_name=candidate.file_name,
            page_start=candidate.page_start,
            page_end=candidate.page_end,
        )

    @staticmethod
    def _merge_sources(
        existing: list[AgentSource],
        incoming: list[AgentSource],
    ) -> list[AgentSource]:
        """按分块 ID 去重，多次不同查询不会重复展示同一来源。"""
        merged = list(existing)
        known = {source.chunk_id for source in merged}
        for source in incoming:
            if source.chunk_id not in known:
                merged.append(source)
                known.add(source.chunk_id)
        return merged

    @staticmethod
    def _tool_signature(name: str, arguments: dict[str, object]) -> str:
        """生成稳定签名，用于拦截完全相同的重复调用。"""
        return name + ":" + json.dumps(
            arguments, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        )

    @staticmethod
    def _strip_json_fence(content: str) -> str:
        """兼容少数模型在 JSON Object 外包裹 Markdown 代码围栏。"""
        stripped = content.strip()
        if stripped.startswith("```") and stripped.endswith("```"):
            stripped = re.sub(r"^```(?:json)?\s*|\s*```$", "", stripped, flags=re.I)
        return stripped.strip()

    @staticmethod
    def _append_step(
        state: AgentGraphState,
        *,
        node: Literal["reason", "tool", "finish", "guard", "fallback"],
        action: str,
        status: Literal["SUCCESS", "ERROR", "BLOCKED"],
        summary: str,
        tool_name: str | None = None,
    ) -> list[AgentStep]:
        """追加单条脱敏步骤，sequence 始终从 1 连续增长。"""
        existing = list(state.get("steps", []))
        existing.append(AgentStep(
            sequence=len(existing) + 1,
            node=node,
            action=action,
            tool_name=tool_name,
            status=status,
            summary=summary[:500],
        ))
        return existing

    @staticmethod
    def _max_score(left: float | None, right: float | None) -> float | None:
        """合并多次检索的最高分。"""
        values = [value for value in (left, right) if value is not None]
        return max(values) if values else None

    @staticmethod
    def _rounded(value: float | None) -> float | None:
        """统一相关度精度。"""
        return None if value is None else round(value, 4)

    @staticmethod
    def _log_route(response: AgentResponse) -> None:
        """只记录 Agent 元数据，不记录问题、回答、档案或工具正文。"""
        logger.info(
            "Agent route stage=%s reason=%s termination=%s tools=%s sourceCount=%s",
            response.stage,
            response.routing_reason,
            response.termination_reason,
            response.tool_call_count,
            len(response.sources),
        )