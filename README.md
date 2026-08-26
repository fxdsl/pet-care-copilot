# 宠里个宠｜宠物智能问答助手

面向新手养宠人的宠物知识问答平台，采用 Vue 3、Spring Boot、FastAPI 三层架构。

## 开发约定

- 后续功能升级时直接迁移到新实现，并删除被替代的旧接口、DTO、占位服务、兼容分支、无用配置和对应测试。
- 不为已经停止使用的前端或接口保留兼容代码；三端契约变更时同步修改 Vue、Spring Boot、FastAPI、测试和文档。
- Flyway 历史迁移文件是数据库版本记录，已经执行后不得删除或修改；表结构变化必须新增下一版本迁移。
- 数据库结构、索引、关系、Redis Key 和存储边界统一维护在[数据库文档](docs/数据库文档.md)；新增迁移时必须在同一次开发中更新。
- 所有新增代码继续添加说明性注释，并保持 Java 的 `controller/service/data/mapper` 分层。
- 从第六周开始，每周文档必须结合真实核心代码讲解调用顺序、输入输出、设计原因、异常路径和调试方法；文档中出现的每个接口还必须列出完整 Path、Query、Body、嵌套对象、响应和错误参数，详细要求见 `docs/开发文档规范.md`。

## 当前进度

- [x] 第一周：需求边界、架构设计、工程骨架、三端连通
- [x] 第二周：MySQL 数据模型、Flyway 迁移、Redis 会话缓存、知识文档清洗与分块导入
- [x] 第三周：Java 分层重构、标准 MyBatis、知识向量化、Top-K 基础 RAG 与来源展示
- [x] 第四周：会话自动创建、多轮上下文、消息持久化、宠物档案与网页知识导入
- [x] 第五周：PDF 导入与扫描件识别、专业 embedding、混合路由优化与 Redis 多轮上下文缓存
- [x] 第六周：LangGraph 受控 ReAct Agent、工具白名单、循环熔断与网页执行轨迹
- [x] 第七周：用户、认证、权限和宠物档案归属
- [x] 第八周：角色化门户、管理员治理、宠物社区发布、媒体存储和异步处理
- [x] 第九周：前端基础重构，评论、点赞、收藏、关注、推荐与举报
- [x] 第十周：他人主页、消息中心、私信、WebSocket 与 Agent SSE 流式回答
- [x] 第十一周：知识投稿/审核工作台、管理员资料上传与可撤回 RAG 发布
- [x] 第十二周：统一搜索、内容发现与 OpenSearch 混合检索
- [ ] 第十三周：个性化推荐、社区治理增强与定时任务
- [ ] 第十四周：全站 UI 验收、网关、稳定性、监控、部署与项目包装

详细开发记录：

- [第一周开发文档](docs/第一周.md)
- [第二周开发文档](docs/第二周.md)
- [第三周开发文档](docs/第三周.md)
- [第四周开发文档](docs/第四周.md)
- [第五周开发文档](docs/第五周.md)
- [第六周开发文档](docs/第六周.md)
- [第七周开发文档](docs/第七周.md)
- [第八周开发文档](docs/第八周.md)
- [第九周开发文档](docs/第九周.md)
- [第十周开发文档](docs/第十周.md)
- [第十一周开发文档](docs/第十一周.md)
- [第十二周开发文档](docs/第十二周.md)
- [数据库文档（随开发持续更新）](docs/数据库文档.md)
- [后续开发路线：宠物社区、知识共建与内容发现](docs/后续开发路线.md)
- [开发文档规范（第六周起强制执行）](docs/开发文档规范.md)

## 工程目录

```text
.
├─ frontend/                         Vue 3 用户界面
├─ backend/business-service/         Spring Boot 业务编排与数据访问
├─ ai-service/                       FastAPI 文档处理与 AI 能力
├─ scripts/                          本地辅助脚本
├─ docs/                             按周维护的开发文档
├─ docker-compose.yml                MySQL、Redis、RabbitMQ、MinIO、OpenSearch 本地容器配置
└─ .env.example                      环境变量示例
```

## 第十二周业务链路

```text
公开内容变更：MySQL 业务事务 → integration_outbox → RabbitMQ 搜索队列 → 重读最新公开状态
索引写入：FastAPI 本地 BGE 文档向量 → OpenSearch 幂等 PUT/DELETE → Redis 索引版本递增
用户查询：URL 恢复筛选 → 版本化结果缓存 → BM25 + KNN 混合检索 → 四类结果分组
安全降级：OpenSearch 故障 → SearchMapper 同权限公开 UNION → backend=MYSQL/degraded=true
内容发现：私人历史写 MySQL；脱敏短查询写 Redis ZSet；Lua 限制联想频率
全量重建：管理员创建任务 → Outbox/RabbitMQ → Redisson Lock → MySQL 公开投影重建 OpenSearch
```

数据库表由 Flyway 管理，当前版本为 `V10`。第十二周增加私人搜索历史和 OpenSearch 重建任务。V1～V10 是不可修改的历史迁移，后续结构变化必须新增 V11。当前 26 张业务表的完整字段、主外键、索引、ER 图和 Redis/MinIO/RabbitMQ/OpenSearch 数据边界见[数据库文档](docs/数据库文档.md)。

## 启动顺序

1. 启动 MySQL `3306`、Redis `6379`、RabbitMQ `5672/15672`、MinIO `9000/9001` 和 OpenSearch。默认 OpenSearch 地址为 `http://localhost:9200`；使用虚拟机时只在本机 `.env` 中覆盖 `OPENSEARCH_ENDPOINT`，不要提交机器专属地址。
2. 在 IDEA Python 或终端启动 FastAPI，端口 `8000`。
3. 在 IDEA Java 运行 `BusinessServiceApplication`，端口 `8080`。
4. 在 VS Code 终端启动 Vue，端口 `5173`。

仅使用普通问答时 RabbitMQ/MinIO/OpenSearch 可以暂时不启动；社区媒体需要 MinIO 和 RabbitMQ，知识投稿预检与发布需要 RabbitMQ、Redis 和 FastAPI。OpenSearch 未启动时统一搜索按相同公开权限降级 MySQL。系统状态页会分别显示每个依赖的 `UP/DOWN`，不会把所有故障都显示成“AI 服务不可用”。

当前 Windows 本机 RabbitMQ 安装在 `G:\develop`，重启电脑后可执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File G:\develop\start-rabbitmq.ps1
# 正常停止
powershell.exe -NoProfile -ExecutionPolicy Bypass -File G:\develop\stop-rabbitmq.ps1
```

```powershell
# FastAPI
cd ai-service
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8000
```

```powershell
# Vue
cd frontend
npm.cmd run dev
```

Spring Boot 的本机数据库参数放在 `backend/business-service/.env`。该文件已被 Git 忽略，不会提交密码；可复制 `.env.example` 后填写自己的值。

首次需要管理员账号时，在本机 `.env` 临时填写 `BOOTSTRAP_ADMIN_USERNAME` 和 `BOOTSTRAP_ADMIN_PASSWORD` 后启动一次 Java。系统只在用户名不存在时创建管理员，不会覆盖已有账号；创建成功后可以清空这两个配置。部署环境还必须使用至少 32 字节的随机 `JWT_SECRET` 覆盖开发默认值。

## 主要接口

| 服务 | 方法与地址 | 用途 |
|---|---|---|
| Spring Boot | `POST /api/v1/auth/register` | 注册并签发令牌对 |
| Spring Boot | `POST /api/v1/auth/login` | 用户名密码登录 |
| Spring Boot | `POST /api/v1/auth/refresh` | 旋转刷新令牌 |
| Spring Boot | `POST /api/v1/auth/logout` | 撤销当前刷新令牌 |
| Spring Boot | `GET/PATCH /api/v1/users/me` | 查询和修改当前用户资料 |
| FastAPI | `GET /api/v1/ai/health` | AI 服务健康检查 |
| FastAPI | `POST /api/v1/knowledge/preprocess` | 文档清洗、校验和、分块与向量化 |
| FastAPI | `POST /api/v1/knowledge/precheck` | 免费规则预检，返回风险标签、摘要与质量分，不调用通用模型 |
| FastAPI | `POST /api/v1/knowledge/pdf/extract` | 提取文字型 PDF，返回页码与预览 |
| FastAPI | `POST /api/v1/knowledge/search/embed` | 为统一搜索生成免费本地 QUERY/DOCUMENT 向量 |
| FastAPI | `POST /api/v1/agent/answer` | 执行 LangGraph Agent，返回脱敏步骤与终止原因 |
| Spring Boot | `GET /api/v1/system/health` | AI、MySQL、Redis、RabbitMQ、MinIO、OpenSearch 聚合状态 |
| Spring Boot | `GET /api/v1/search` | 四类公开内容的分组搜索与安全降级 |
| Spring Boot | `GET /api/v1/search/suggestions` | 私人历史、脱敏趋势和公开标题联想 |
| Spring Boot | `GET/DELETE /api/v1/search/history[/{id}]` | 查询、单删或清空当前用户搜索历史 |
| Spring Boot | `GET /api/v1/search/trending` | 查询 Redis 脱敏搜索趋势 |
| Spring Boot | `POST /api/v1/admin/search/rebuild` | 管理员异步发起 OpenSearch 全量重建 |
| Spring Boot | `GET /api/v1/admin/search/rebuild/{jobId}` | 查询索引重建任务事实进度 |
| Spring Boot | `POST /api/v1/chat/preview` | 多轮 Agent 入口，自动管理会话并保存消息 |
| Spring Boot | `POST /api/v1/chat/stream` | Agent SSE 流式回答、心跳、重连与完成事件 |
| Spring Boot | `DELETE /api/v1/chat/streams/{requestId}` | 停止当前流式回答 |
| Spring Boot | `GET /api/v1/community/users/{userId}/profile` | 查询社区用户公开主页与宠物摘要 |
| Spring Boot | `GET /api/v1/messages/unread` | 查询通知与私信未读角标 |
| Spring Boot | `GET /api/v1/messages/notifications` | 分页查询站内通知 |
| Spring Boot | `GET /api/v1/messages/conversations` | 分页查询私信会话 |
| Spring Boot | `POST /api/v1/messages/direct` | 使用 clientMessageId 幂等发送私信 |
| Spring Boot | `WS /ws/realtime` | 首帧 JWT 认证的实时通知与私信提示 |
| Spring Boot | `POST /api/v1/conversations` | 创建会话 |
| Spring Boot | `GET /api/v1/conversations` | 查询最近会话列表 |
| Spring Boot | `GET /api/v1/conversations/{id}/messages` | 从 MySQL 读取完整历史消息 |
| Spring Boot | `POST /api/v1/conversations/{id}/messages` | 添加消息 |
| Spring Boot | `POST /api/v1/knowledge-submissions/community` | 普通用户将自己的已发布帖子提交知识审核 |
| Spring Boot | `GET /api/v1/knowledge-submissions/mine` | 分页查询当前用户的投稿 |
| Spring Boot | `GET/DELETE /api/v1/knowledge-submissions/{id}` | 查询或撤回自己的投稿 |
| Spring Boot | `POST /api/v1/admin/knowledge-submissions/uploads` | 管理员登记资料并创建预检任务 |
| Spring Boot | `GET /api/v1/admin/knowledge-submissions` | 按状态、风险、来源分页查询审核队列 |
| Spring Boot | `GET /api/v1/admin/knowledge-submissions/stats` | 查询知识治理统计卡片 |
| Spring Boot | `GET /api/v1/admin/knowledge-submissions/{id}` | 查询原文、清洗文和审核时间线 |
| Spring Boot | `POST /api/v1/admin/knowledge-submissions/{id}/review` | 人工批准或驳回指定投稿版本 |
| Spring Boot | `POST /api/v1/knowledge/documents/pdf/extract` | 网页 PDF 提取入口 |
| Spring Boot | `POST /api/v1/knowledge/documents/reindex` | 使用当前模型重建全部知识向量 |
| Spring Boot | `GET /api/v1/knowledge/documents` | 查询知识文档 |
| Spring Boot | `POST /api/v1/pet-profiles` | 创建宠物档案 |
| Spring Boot | `GET /api/v1/pet-profiles` | 查询宠物档案 |
| Spring Boot | `PUT/DELETE /api/v1/pet-profiles/{id}` | 修改或删除当前用户自己的档案 |
| Spring Boot | `GET /api/v1/admin/users` | 管理员分页查询用户 |
| Spring Boot | `PATCH /api/v1/admin/users/{id}/role` | 调整角色并使旧权限凭证失效 |
| Spring Boot | `PATCH /api/v1/admin/users/{id}/status` | 禁用或恢复账号 |
| Spring Boot | `GET /api/v1/admin/audit-logs` | 查询管理员操作审计 |
| Spring Boot | `POST /api/v1/knowledge/documents/test-answer` | 管理员独立 RAG 测试，不创建普通会话 |
| Spring Boot | `GET/POST /api/v1/community/posts` | 社区公开列表/创建草稿 |
| Spring Boot | `PUT/DELETE /api/v1/community/posts/{id}` | 编辑或逻辑删除自己的帖子 |
| Spring Boot | `POST /api/v1/community/posts/{id}/publish` | 发布自己的帖子并写 Outbox |
| Spring Boot | `POST /api/v1/community/media/upload-url` | 申请 MinIO 预签名上传地址 |
| Spring Boot | `POST /api/v1/community/media/{id}/confirm` | 核对并确认已上传媒体 |
| Spring Boot | `GET /api/v1/community/posts?feed=LATEST/HOT/FOLLOWING/NEARBY` | 四类社区信息流 |
| Spring Boot | `GET/POST /api/v1/community/posts/{id}/comments` | 查询评论或发表评论/回复 |
| Spring Boot | `PUT/DELETE /api/v1/community/posts/{id}/like` | 幂等点赞/取消点赞 |
| Spring Boot | `PUT/DELETE /api/v1/community/posts/{id}/favorite` | 幂等收藏/取消收藏 |
| Spring Boot | `PUT/DELETE /api/v1/community/users/{id}/follow` | 关注/取消关注用户 |
| Spring Boot | `POST /api/v1/community/reports` | 举报帖子、评论或用户 |
| Spring Boot | `GET/PUT /api/v1/community/check-ins/today` | 查询或完成今日养宠打卡 |
| Spring Boot | `GET/PUT /api/v1/moderation/community/reports[/{id}]` | 管理端举报队列与处理 |
| Spring Boot | `GET /api/v1/moderation/community/analytics/today` | 社区近似 UV 与治理积压 |

> 第十二周所有搜索/索引接口的 Path、Query、Body、响应、错误参数，以及 OpenSearch、Redis 和 RabbitMQ 的数据边界见[第十二周开发文档](docs/第十二周.md)。第十一周知识投稿与审核契约见[第十一周开发文档](docs/第十一周.md)。
