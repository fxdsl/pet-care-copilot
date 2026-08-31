-- 为全部业务表和字段补充中文数据字典注释。
-- 仅修改 MySQL 元数据，不改变业务数据、索引、外键或字段语义。

ALTER TABLE `admin_audit_log` COMMENT = '管理员治理操作审计记录';
ALTER TABLE `admin_audit_log`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '管理员审计记录主键 UUID',
    MODIFY COLUMN `actor_user_id` char(36) NOT NULL COMMENT '执行操作的用户 ID',
    MODIFY COLUMN `target_user_id` char(36) NULL DEFAULT NULL COMMENT '被操作的目标用户 ID',
    MODIFY COLUMN `action` varchar(60) NOT NULL COMMENT '管理员操作类型',
    MODIFY COLUMN `before_value` varchar(500) NULL DEFAULT NULL COMMENT '变更前的安全摘要',
    MODIFY COLUMN `after_value` varchar(500) NULL DEFAULT NULL COMMENT '变更后的安全摘要',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '管理员操作发生时间';

ALTER TABLE `admin_audit_log_archive` COMMENT = '超过保留期的管理员审计归档记录';
ALTER TABLE `admin_audit_log_archive`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '沿用原管理员审计记录 UUID',
    MODIFY COLUMN `actor_user_id` char(36) NOT NULL COMMENT '执行操作的用户 ID',
    MODIFY COLUMN `target_user_id` char(36) NULL DEFAULT NULL COMMENT '被操作的目标用户 ID',
    MODIFY COLUMN `action` varchar(60) NOT NULL COMMENT '管理员操作类型',
    MODIFY COLUMN `before_value` varchar(500) NULL DEFAULT NULL COMMENT '变更前的安全摘要',
    MODIFY COLUMN `after_value` varchar(500) NULL DEFAULT NULL COMMENT '变更后的安全摘要',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL COMMENT '原管理员操作发生时间',
    MODIFY COLUMN `archived_at` timestamp(6) NOT NULL COMMENT '归档时间';

ALTER TABLE `answer_feedback` COMMENT = '智能问答回答的点赞或点踩反馈';
ALTER TABLE `answer_feedback`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '回答反馈主键 UUID',
    MODIFY COLUMN `message_id` char(36) NOT NULL COMMENT '关联消息 ID',
    MODIFY COLUMN `rating` tinyint NOT NULL COMMENT '评价值：1 点赞，-1 点踩',
    MODIFY COLUMN `comment` varchar(1000) NULL DEFAULT NULL COMMENT '用户补充说明',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '反馈提交时间';

ALTER TABLE `app_user` COMMENT = '用户账号、认证信息、角色权限和公开资料';
ALTER TABLE `app_user`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '用户主键 UUID',
    MODIFY COLUMN `username` varchar(64) NOT NULL COMMENT '唯一登录用户名',
    MODIFY COLUMN `password_hash` varchar(100) NOT NULL COMMENT 'BCrypt 密码散列，禁止返回前端',
    MODIFY COLUMN `display_name` varchar(100) NULL DEFAULT NULL COMMENT '对外展示昵称',
    MODIFY COLUMN `role` varchar(30) NOT NULL DEFAULT 'USER' COMMENT '角色：USER、VERIFIED_SELLER、MODERATOR 或 ADMIN',
    MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE、DISABLED 或 LOCKED',
    MODIFY COLUMN `security_version` bigint NOT NULL DEFAULT 1 COMMENT '安全版本；角色或状态变化时递增使旧 JWT 失效',
    MODIFY COLUMN `avatar_url` varchar(1000) NULL DEFAULT NULL COMMENT '头像访问地址',
    MODIFY COLUMN `bio` varchar(500) NULL DEFAULT NULL COMMENT '个人简介',
    MODIFY COLUMN `region` varchar(100) NULL DEFAULT NULL COMMENT '地区文本',
    MODIFY COLUMN `last_login_at` timestamp(6) NULL DEFAULT NULL COMMENT '最近一次成功登录时间',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间';

ALTER TABLE `community_checkin` COMMENT = '用户每日签到事实记录';
ALTER TABLE `community_checkin`
    MODIFY COLUMN `user_id` char(36) NOT NULL COMMENT '关联用户 ID',
    MODIFY COLUMN `checkin_date` date NOT NULL COMMENT '签到日期',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '签到记录写入时间';

ALTER TABLE `community_comment` COMMENT = '社区帖子评论与二级回复';
ALTER TABLE `community_comment`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '评论主键 UUID',
    MODIFY COLUMN `post_id` char(36) NOT NULL COMMENT '关联社区帖子 ID',
    MODIFY COLUMN `author_id` char(36) NOT NULL COMMENT '内容作者用户 ID',
    MODIFY COLUMN `parent_id` char(36) NULL DEFAULT NULL COMMENT '直接父评论 ID',
    MODIFY COLUMN `root_id` char(36) NULL DEFAULT NULL COMMENT '所属根评论 ID',
    MODIFY COLUMN `depth` tinyint NOT NULL DEFAULT 0 COMMENT '评论层级：0 根评论，1 二级回复',
    MODIFY COLUMN `content` varchar(2000) NOT NULL COMMENT '评论正文',
    MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT '评论状态：PUBLISHED、HIDDEN 或 DELETED',
    MODIFY COLUMN `like_count` bigint NOT NULL DEFAULT 0 COMMENT '点赞数量快照',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间';

ALTER TABLE `community_media` COMMENT = '社区图片和视频在 MinIO 中的对象元数据';
ALTER TABLE `community_media`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '媒体记录主键 UUID',
    MODIFY COLUMN `owner_id` char(36) NOT NULL COMMENT '媒体所有者用户 ID',
    MODIFY COLUMN `post_id` char(36) NULL DEFAULT NULL COMMENT '绑定的帖子 ID；尚未绑定时为空',
    MODIFY COLUMN `object_key` varchar(600) NOT NULL COMMENT 'MinIO 私有桶中的对象键',
    MODIFY COLUMN `original_filename` varchar(255) NOT NULL COMMENT '用户上传时的原始文件名',
    MODIFY COLUMN `media_type` varchar(20) NOT NULL COMMENT '媒体类型：IMAGE 或 VIDEO',
    MODIFY COLUMN `content_type` varchar(100) NOT NULL COMMENT '文件 MIME 类型',
    MODIFY COLUMN `size_bytes` bigint NOT NULL COMMENT '文件字节数',
    MODIFY COLUMN `checksum_sha256` char(64) NULL DEFAULT NULL COMMENT '文件内容 SHA-256 校验值',
    MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '上传状态：PENDING、CONFIRMED 或 REJECTED',
    MODIFY COLUMN `processing_status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT '媒体处理状态：WAITING、PROCESSING、READY 或 FAILED',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `confirmed_at` timestamp(6) NULL DEFAULT NULL COMMENT '对象上传确认时间';

ALTER TABLE `community_post` COMMENT = '社区帖子正文、发布状态与互动计数快照';
ALTER TABLE `community_post`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '帖子主键 UUID',
    MODIFY COLUMN `author_id` char(36) NOT NULL COMMENT '内容作者用户 ID',
    MODIFY COLUMN `pet_profile_id` char(36) NULL DEFAULT NULL COMMENT '帖子关联的宠物档案 ID',
    MODIFY COLUMN `topic_id` char(36) NULL DEFAULT NULL COMMENT '帖子关联的话题 ID',
    MODIFY COLUMN `title` varchar(160) NOT NULL COMMENT '帖子标题',
    MODIFY COLUMN `content` text NOT NULL COMMENT '帖子正文',
    MODIFY COLUMN `region` varchar(100) NULL DEFAULT NULL COMMENT '地区文本',
    MODIFY COLUMN `latitude` decimal(10,7) NULL DEFAULT NULL COMMENT '纬度，范围 -90 至 90',
    MODIFY COLUMN `longitude` decimal(10,7) NULL DEFAULT NULL COMMENT '经度，范围 -180 至 180',
    MODIFY COLUMN `status` varchar(30) NOT NULL DEFAULT 'DRAFT' COMMENT '帖子状态：DRAFT、PENDING_REVIEW、PUBLISHED、HIDDEN 或 DELETED',
    MODIFY COLUMN `view_count` bigint NOT NULL DEFAULT 0 COMMENT '帖子浏览量持久化基数',
    MODIFY COLUMN `like_count` bigint NOT NULL DEFAULT 0 COMMENT '点赞数量快照',
    MODIFY COLUMN `comment_count` bigint NOT NULL DEFAULT 0 COMMENT '公开评论数量快照',
    MODIFY COLUMN `favorite_count` bigint NOT NULL DEFAULT 0 COMMENT '收藏数量快照',
    MODIFY COLUMN `repost_count` bigint NOT NULL DEFAULT 0 COMMENT '有效转发数量快照',
    MODIFY COLUMN `version` int NOT NULL DEFAULT 1 COMMENT '编辑和治理使用的乐观锁版本',
    MODIFY COLUMN `published_at` timestamp(6) NULL DEFAULT NULL COMMENT '发布时间',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间';

ALTER TABLE `community_post_favorite` COMMENT = '用户收藏帖子关系';
ALTER TABLE `community_post_favorite`
    MODIFY COLUMN `post_id` char(36) NOT NULL COMMENT '关联社区帖子 ID',
    MODIFY COLUMN `user_id` char(36) NOT NULL COMMENT '关联用户 ID',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '首次收藏时间';

ALTER TABLE `community_post_like` COMMENT = '用户点赞帖子关系';
ALTER TABLE `community_post_like`
    MODIFY COLUMN `post_id` char(36) NOT NULL COMMENT '关联社区帖子 ID',
    MODIFY COLUMN `user_id` char(36) NOT NULL COMMENT '关联用户 ID',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '点赞时间';

ALTER TABLE `community_post_repost` COMMENT = '用户转发帖子关系及引用内容';
ALTER TABLE `community_post_repost`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '转发关系主键 UUID',
    MODIFY COLUMN `post_id` char(36) NOT NULL COMMENT '关联社区帖子 ID',
    MODIFY COLUMN `user_id` char(36) NOT NULL COMMENT '关联用户 ID',
    MODIFY COLUMN `quote_content` varchar(500) NULL DEFAULT NULL COMMENT '转发时附加的引用内容',
    MODIFY COLUMN `active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '关系是否当前有效',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间';

ALTER TABLE `community_recommendation_feedback` COMMENT = '用户对社区推荐结果的反馈';
ALTER TABLE `community_recommendation_feedback`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '推荐反馈主键 UUID',
    MODIFY COLUMN `user_id` char(36) NOT NULL COMMENT '关联用户 ID',
    MODIFY COLUMN `post_id` char(36) NOT NULL COMMENT '关联社区帖子 ID',
    MODIFY COLUMN `feedback_type` varchar(30) NOT NULL COMMENT '反馈类型，当前为 NOT_INTERESTED',
    MODIFY COLUMN `active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '关系是否当前有效',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间';

ALTER TABLE `community_report` COMMENT = '社区帖子、评论或用户举报及处理结果';
ALTER TABLE `community_report`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '举报记录主键 UUID',
    MODIFY COLUMN `reporter_id` char(36) NOT NULL COMMENT '举报用户 ID',
    MODIFY COLUMN `target_type` varchar(20) NOT NULL COMMENT '业务目标类型',
    MODIFY COLUMN `target_id` char(36) NOT NULL COMMENT '业务目标 ID',
    MODIFY COLUMN `reason_type` varchar(30) NOT NULL COMMENT '举报原因类型',
    MODIFY COLUMN `description` varchar(1000) NULL DEFAULT NULL COMMENT '补充描述',
    MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '举报状态：PENDING、RESOLVED 或 REJECTED',
    MODIFY COLUMN `resolution` varchar(30) NULL DEFAULT NULL COMMENT '治理处理结论',
    MODIFY COLUMN `moderator_id` char(36) NULL DEFAULT NULL COMMENT '处理举报的管理员 ID',
    MODIFY COLUMN `moderator_note` varchar(1000) NULL DEFAULT NULL COMMENT '管理员处理备注',
    MODIFY COLUMN `version` int NOT NULL DEFAULT 1 COMMENT '举报处理乐观锁版本',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `resolved_at` timestamp(6) NULL DEFAULT NULL COMMENT '处理完成时间';

ALTER TABLE `community_topic` COMMENT = '社区帖子话题分类字典';
ALTER TABLE `community_topic`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '话题主键',
    MODIFY COLUMN `name` varchar(80) NOT NULL COMMENT '唯一话题名称',
    MODIFY COLUMN `description` varchar(300) NULL DEFAULT NULL COMMENT '话题说明',
    MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '话题状态：ACTIVE 或 DISABLED',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';
ALTER TABLE `community_trend_snapshot` COMMENT = '社区热门帖子定时排名快照';
ALTER TABLE `community_trend_snapshot`
    MODIFY COLUMN `snapshot_at` timestamp(6) NOT NULL COMMENT '趋势快照时间',
    MODIFY COLUMN `post_id` char(36) NOT NULL COMMENT '进入榜单的帖子 ID',
    MODIFY COLUMN `rank_no` int NOT NULL COMMENT '快照内排名序号',
    MODIFY COLUMN `score` decimal(18,6) NOT NULL COMMENT '推荐或趋势计算得分',
    MODIFY COLUMN `reason` varchar(200) NOT NULL COMMENT '进入趋势榜的主要原因';

ALTER TABLE `community_user_follow` COMMENT = '用户之间的关注关系';
ALTER TABLE `community_user_follow`
    MODIFY COLUMN `follower_id` char(36) NOT NULL COMMENT '发起关注的用户 ID',
    MODIFY COLUMN `followed_id` char(36) NOT NULL COMMENT '被关注的用户 ID',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '关注关系创建时间';

ALTER TABLE `community_user_relation_control` COMMENT = '用户屏蔽或拉黑关系';
ALTER TABLE `community_user_relation_control`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '用户治理关系主键 UUID',
    MODIFY COLUMN `actor_user_id` char(36) NOT NULL COMMENT '执行屏蔽或拉黑的用户 ID',
    MODIFY COLUMN `target_user_id` char(36) NOT NULL COMMENT '被屏蔽或拉黑的用户 ID',
    MODIFY COLUMN `relation_type` varchar(20) NOT NULL COMMENT '关系类型：MUTE 屏蔽或 BLOCK 拉黑',
    MODIFY COLUMN `active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '关系是否当前有效',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间';

ALTER TABLE `conversation` COMMENT = '智能问答会话摘要';
ALTER TABLE `conversation`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '智能问答会话主键 UUID',
    MODIFY COLUMN `user_id` char(36) NULL DEFAULT NULL COMMENT '会话所属用户 ID；用户删除后可为空',
    MODIFY COLUMN `title` varchar(200) NOT NULL COMMENT '根据首问生成的会话标题',
    MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态，当前使用 ACTIVE',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '新增消息时刷新，用于历史会话排序';

ALTER TABLE `direct_conversation` COMMENT = '两位用户之间唯一的私信会话';
ALTER TABLE `direct_conversation`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '私信会话主键 UUID',
    MODIFY COLUMN `participant_low_id` char(36) NOT NULL COMMENT '按字典序较小的会话参与者 ID',
    MODIFY COLUMN `participant_high_id` char(36) NOT NULL COMMENT '按字典序较大的会话参与者 ID',
    MODIFY COLUMN `last_message_at` timestamp(6) NULL DEFAULT NULL COMMENT '最近一条私信时间',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最近消息写入时更新时间';

ALTER TABLE `direct_message` COMMENT = '私信正文、幂等标识与已读状态';
ALTER TABLE `direct_message`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '私信消息主键 UUID',
    MODIFY COLUMN `conversation_id` char(36) NOT NULL COMMENT '所属会话 ID',
    MODIFY COLUMN `sender_id` char(36) NOT NULL COMMENT '发送用户 ID',
    MODIFY COLUMN `recipient_id` char(36) NOT NULL COMMENT '接收用户 ID',
    MODIFY COLUMN `client_message_id` char(36) NOT NULL COMMENT '前端生成并在重试时复用的幂等 UUID',
    MODIFY COLUMN `content` varchar(2000) NOT NULL COMMENT '私信正文',
    MODIFY COLUMN `read_at` timestamp(6) NULL DEFAULT NULL COMMENT '读取时间；为空表示未读',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

ALTER TABLE `integration_event_manual_review` COMMENT = '自动重试超限的 Outbox 事件人工处理清单';
ALTER TABLE `integration_event_manual_review`
    MODIFY COLUMN `event_id` char(36) NOT NULL COMMENT '主键，同时关联 Outbox 事件 ID',
    MODIFY COLUMN `reason` varchar(300) NOT NULL COMMENT '事件转入人工处理的原因',
    MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '人工处理状态：PENDING 或 RESOLVED',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `resolved_at` timestamp(6) NULL DEFAULT NULL COMMENT '处理完成时间';

ALTER TABLE `integration_outbox` COMMENT = '可靠异步事件的事务发件箱';
ALTER TABLE `integration_outbox`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT 'Outbox 事件主键 UUID',
    MODIFY COLUMN `aggregate_type` varchar(50) NOT NULL COMMENT '事件所属聚合类型',
    MODIFY COLUMN `aggregate_id` char(36) NOT NULL COMMENT '事件所属业务聚合 ID',
    MODIFY COLUMN `event_type` varchar(80) NOT NULL COMMENT '集成事件类型',
    MODIFY COLUMN `payload_json` text NOT NULL COMMENT '事件负载 JSON',
    MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '发布状态：PENDING、PROCESSING、PUBLISHED 或 FAILED',
    MODIFY COLUMN `attempts` int NOT NULL DEFAULT 0 COMMENT '已执行或重试次数',
    MODIFY COLUMN `next_attempt_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次允许重试时间',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `published_at` timestamp(6) NULL DEFAULT NULL COMMENT '成功发布到 RabbitMQ 的时间';

ALTER TABLE `knowledge_chunk` COMMENT = 'RAG 知识文档分块、页码与向量数据';
ALTER TABLE `knowledge_chunk`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '知识分块主键 UUID',
    MODIFY COLUMN `document_id` char(36) NOT NULL COMMENT '所属知识文档 ID',
    MODIFY COLUMN `chunk_index` int NOT NULL COMMENT '文档内从 0 开始的分块序号',
    MODIFY COLUMN `content` text NOT NULL COMMENT '分块正文',
    MODIFY COLUMN `char_count` int NOT NULL COMMENT '分块字符数量',
    MODIFY COLUMN `token_estimate` int NOT NULL COMMENT '分块 Token 估算值',
    MODIFY COLUMN `embedding_json` json NULL DEFAULT NULL COMMENT '向量数组 JSON',
    MODIFY COLUMN `embedding_model` varchar(100) NULL DEFAULT NULL COMMENT '生成向量的模型名称',
    MODIFY COLUMN `embedding_dimensions` smallint NULL DEFAULT NULL COMMENT '向量维度',
    MODIFY COLUMN `page_start` int NULL DEFAULT NULL COMMENT 'PDF 来源起始页码',
    MODIFY COLUMN `page_end` int NULL DEFAULT NULL COMMENT 'PDF 来源结束页码',
    MODIFY COLUMN `embedded_at` timestamp(6) NULL DEFAULT NULL COMMENT '最近一次向量化时间',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

ALTER TABLE `knowledge_document` COMMENT = '审核通过并发布到 RAG 的知识文档';
ALTER TABLE `knowledge_document`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '知识文档主键 UUID',
    MODIFY COLUMN `submission_id` char(36) NULL DEFAULT NULL COMMENT '关联知识投稿 ID',
    MODIFY COLUMN `title` varchar(300) NOT NULL COMMENT '知识文档标题',
    MODIFY COLUMN `source_type` varchar(30) NOT NULL DEFAULT 'ADMIN_UPLOAD' COMMENT '来源类型：ADMIN_UPLOAD 或 COMMUNITY_POST',
    MODIFY COLUMN `source_business_id` char(36) NULL DEFAULT NULL COMMENT '来源业务对象 ID',
    MODIFY COLUMN `source_url` varchar(1000) NULL DEFAULT NULL COMMENT '原始来源链接',
    MODIFY COLUMN `source_name` varchar(200) NULL DEFAULT NULL COMMENT '来源机构或名称',
    MODIFY COLUMN `source_author` varchar(120) NULL DEFAULT NULL COMMENT '原始资料作者',
    MODIFY COLUMN `author_user_id` char(36) NULL DEFAULT NULL COMMENT '平台投稿用户 ID',
    MODIFY COLUMN `file_name` varchar(255) NULL DEFAULT NULL COMMENT '导入文件名',
    MODIFY COLUMN `document_type` varchar(20) NOT NULL DEFAULT 'TEXT' COMMENT '文档类型：TEXT 或 PDF',
    MODIFY COLUMN `pet_type` varchar(30) NOT NULL COMMENT '宠物类型：CAT、DOG 或 OTHER',
    MODIFY COLUMN `category` varchar(50) NOT NULL COMMENT '知识分类',
    MODIFY COLUMN `language_code` varchar(10) NOT NULL DEFAULT 'zh-CN' COMMENT '内容语言代码',
    MODIFY COLUMN `content` longtext NOT NULL COMMENT '审核发布后的清洗完整正文',
    MODIFY COLUMN `content_checksum` char(64) NOT NULL COMMENT '正文 SHA-256，用于去重',
    MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'READY' COMMENT '检索状态：READY、SUPERSEDED 或 WITHDRAWN',
    MODIFY COLUMN `review_status` varchar(20) NOT NULL DEFAULT 'APPROVED' COMMENT '检索要求的审核状态，当前为 APPROVED',
    MODIFY COLUMN `trust_level` char(1) NOT NULL DEFAULT 'A' COMMENT '来源信任等级：A、B 或 C',
    MODIFY COLUMN `quality_score` decimal(5,2) NULL DEFAULT NULL COMMENT 'AI 预检质量分，范围 0 至 100',
    MODIFY COLUMN `consent_status` varchar(20) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '授权状态：GRANTED、NOT_REQUIRED 或 WITHDRAWN',
    MODIFY COLUMN `reviewer_user_id` char(36) NULL DEFAULT NULL COMMENT '审核管理员 ID',
    MODIFY COLUMN `reviewed_at` timestamp(6) NULL DEFAULT NULL COMMENT '人工审核时间',
    MODIFY COLUMN `version` int NOT NULL DEFAULT 1 COMMENT '对应投稿的发布版本号',
    MODIFY COLUMN `published_at` timestamp(6) NULL DEFAULT NULL COMMENT '发布时间',
    MODIFY COLUMN `expires_at` timestamp(6) NULL DEFAULT NULL COMMENT '内容有效期；为空表示长期有效',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '文档导入时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间';

ALTER TABLE `knowledge_review_record` COMMENT = '知识投稿的追加式审核时间线';
ALTER TABLE `knowledge_review_record`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '审核记录主键 UUID',
    MODIFY COLUMN `submission_id` char(36) NOT NULL COMMENT '关联知识投稿 ID',
    MODIFY COLUMN `version` int NOT NULL COMMENT '本次审核针对的投稿版本',
    MODIFY COLUMN `reviewer_user_id` char(36) NULL DEFAULT NULL COMMENT '审核管理员 ID',
    MODIFY COLUMN `action` varchar(30) NOT NULL COMMENT '审核动作：SUBMITTED、PRECHECK_COMPLETED、APPROVED、REJECTED、PUBLISHED、WITHDRAWN 或 FAILED',
    MODIFY COLUMN `trust_level` char(1) NULL DEFAULT NULL COMMENT '来源信任等级：A、B 或 C',
    MODIFY COLUMN `reason` varchar(1000) NULL DEFAULT NULL COMMENT '审核意见、失败原因或操作说明',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

ALTER TABLE `knowledge_submission` COMMENT = '知识投稿、预检、审核和发布状态机主记录';
ALTER TABLE `knowledge_submission`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '知识投稿主键 UUID',
    MODIFY COLUMN `source_type` varchar(30) NOT NULL COMMENT '投稿来源：COMMUNITY_POST 或 ADMIN_UPLOAD',
    MODIFY COLUMN `source_business_id` char(36) NULL DEFAULT NULL COMMENT '来源业务对象 ID',
    MODIFY COLUMN `author_user_id` char(36) NULL DEFAULT NULL COMMENT '平台投稿用户 ID',
    MODIFY COLUMN `title` varchar(300) NOT NULL COMMENT '投稿标题',
    MODIFY COLUMN `source_name` varchar(200) NULL DEFAULT NULL COMMENT '来源机构或名称',
    MODIFY COLUMN `source_author` varchar(120) NULL DEFAULT NULL COMMENT '原始资料作者',
    MODIFY COLUMN `source_url` varchar(1000) NULL DEFAULT NULL COMMENT '原始来源链接',
    MODIFY COLUMN `file_name` varchar(255) NULL DEFAULT NULL COMMENT '导入文件名',
    MODIFY COLUMN `document_type` varchar(20) NOT NULL DEFAULT 'TEXT' COMMENT '文档类型：TEXT 或 PDF',
    MODIFY COLUMN `pet_type` varchar(30) NOT NULL COMMENT '宠物类型：CAT、DOG 或 OTHER',
    MODIFY COLUMN `category` varchar(50) NOT NULL COMMENT '知识分类',
    MODIFY COLUMN `original_content` longtext NOT NULL COMMENT '用户提交的原始正文',
    MODIFY COLUMN `cleaned_content` longtext NULL DEFAULT NULL COMMENT '预检清洗后的正文',
    MODIFY COLUMN `content_checksum` char(64) NULL DEFAULT NULL COMMENT '正文 SHA-256，用于去重',
    MODIFY COLUMN `consent_status` varchar(20) NOT NULL COMMENT '发布授权状态：GRANTED、NOT_REQUIRED 或 WITHDRAWN',
    MODIFY COLUMN `status` varchar(30) NOT NULL DEFAULT 'PRECHECKING' COMMENT '投稿状态：PRECHECKING、PENDING_REVIEW、APPROVED、REJECTED、PUBLISHING、PUBLISHED、WITHDRAWN 或 FAILED',
    MODIFY COLUMN `risk_level` varchar(20) NULL DEFAULT NULL COMMENT '预检风险等级：LOW、MEDIUM 或 HIGH',
    MODIFY COLUMN `risk_labels` varchar(1000) NULL DEFAULT NULL COMMENT '预检命中的风险标签',
    MODIFY COLUMN `ai_summary` varchar(1000) NULL DEFAULT NULL COMMENT 'AI 生成的内容摘要',
    MODIFY COLUMN `quality_score` decimal(5,2) NULL DEFAULT NULL COMMENT 'AI 预检质量分，范围 0 至 100',
    MODIFY COLUMN `current_version` int NOT NULL DEFAULT 1 COMMENT '投稿当前版本号',
    MODIFY COLUMN `reviewer_user_id` char(36) NULL DEFAULT NULL COMMENT '审核管理员 ID',
    MODIFY COLUMN `reviewed_at` timestamp(6) NULL DEFAULT NULL COMMENT '人工审核时间',
    MODIFY COLUMN `published_document_id` char(36) NULL DEFAULT NULL COMMENT '发布后生成的知识文档 ID',
    MODIFY COLUMN `source_published_at` timestamp(6) NULL DEFAULT NULL COMMENT '原始资料发布日期',
    MODIFY COLUMN `published_at` timestamp(6) NULL DEFAULT NULL COMMENT '发布时间',
    MODIFY COLUMN `expires_at` timestamp(6) NULL DEFAULT NULL COMMENT '内容有效期；为空表示长期有效',
    MODIFY COLUMN `error_message` varchar(1000) NULL DEFAULT NULL COMMENT '失败或驳回的安全错误摘要',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间';

ALTER TABLE `knowledge_submission_version` COMMENT = '知识投稿每次提交的不可变版本快照';
ALTER TABLE `knowledge_submission_version`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '投稿版本快照主键 UUID',
    MODIFY COLUMN `submission_id` char(36) NOT NULL COMMENT '关联知识投稿 ID',
    MODIFY COLUMN `version` int NOT NULL COMMENT '投稿版本号',
    MODIFY COLUMN `title` varchar(300) NOT NULL COMMENT '该版本投稿标题',
    MODIFY COLUMN `original_content` longtext NOT NULL COMMENT '用户提交的原始正文',
    MODIFY COLUMN `cleaned_content` longtext NULL DEFAULT NULL COMMENT '预检清洗后的正文',
    MODIFY COLUMN `content_checksum` char(64) NULL DEFAULT NULL COMMENT '正文 SHA-256，用于去重',
    MODIFY COLUMN `ai_summary` varchar(1000) NULL DEFAULT NULL COMMENT 'AI 生成的内容摘要',
    MODIFY COLUMN `risk_level` varchar(20) NULL DEFAULT NULL COMMENT '预检风险等级：LOW、MEDIUM 或 HIGH',
    MODIFY COLUMN `risk_labels` varchar(1000) NULL DEFAULT NULL COMMENT '预检命中的风险标签',
    MODIFY COLUMN `quality_score` decimal(5,2) NULL DEFAULT NULL COMMENT 'AI 预检质量分，范围 0 至 100',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

ALTER TABLE `message` COMMENT = '智能问答会话的完整消息事实';
ALTER TABLE `message`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '智能问答消息主键 UUID',
    MODIFY COLUMN `conversation_id` char(36) NOT NULL COMMENT '所属会话 ID',
    MODIFY COLUMN `role` varchar(20) NOT NULL COMMENT '消息角色：USER、ASSISTANT、SYSTEM 或 TOOL',
    MODIFY COLUMN `content` text NOT NULL COMMENT '消息完整正文',
    MODIFY COLUMN `model_name` varchar(100) NULL DEFAULT NULL COMMENT '生成助手消息所使用的模型名称',
    MODIFY COLUMN `token_count` int NULL DEFAULT NULL COMMENT '消息 Token 统计或估算值',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

ALTER TABLE `pet_profile` COMMENT = '用户宠物的基础档案';
ALTER TABLE `pet_profile`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '宠物档案主键 UUID',
    MODIFY COLUMN `user_id` char(36) NULL DEFAULT NULL COMMENT '档案所有者用户 ID；历史匿名档案可为空',
    MODIFY COLUMN `name` varchar(80) NOT NULL COMMENT '宠物名称',
    MODIFY COLUMN `pet_type` varchar(30) NOT NULL COMMENT '宠物类型：CAT、DOG 或 OTHER',
    MODIFY COLUMN `breed` varchar(100) NULL DEFAULT NULL COMMENT '宠物品种',
    MODIFY COLUMN `age_months` int NULL DEFAULT NULL COMMENT '宠物月龄，范围 0 至 600',
    MODIFY COLUMN `weight_kg` decimal(6,2) NULL DEFAULT NULL COMMENT '宠物体重，单位千克',
    MODIFY COLUMN `notes` varchar(1000) NULL DEFAULT NULL COMMENT '宠物档案备注',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间';

ALTER TABLE `processed_integration_event` COMMENT = 'RabbitMQ 消费者幂等处理凭证';
ALTER TABLE `processed_integration_event`
    MODIFY COLUMN `event_id` char(36) NOT NULL COMMENT '联合主键中的 Outbox 事件 ID',
    MODIFY COLUMN `consumer_name` varchar(80) NOT NULL COMMENT '消费者唯一名称',
    MODIFY COLUMN `processed_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '消费者成功认领时间';

ALTER TABLE `scheduled_job_execution` COMMENT = 'Quartz 定时任务执行审计与重试状态';
ALTER TABLE `scheduled_job_execution`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '定时任务执行记录主键 UUID',
    MODIFY COLUMN `job_name` varchar(80) NOT NULL COMMENT '定时任务名称',
    MODIFY COLUMN `batch_key` varchar(120) NOT NULL COMMENT '任务幂等批次键',
    MODIFY COLUMN `status` varchar(20) NOT NULL COMMENT '执行状态：RUNNING、COMPLETED、FAILED 或 MANUAL_REVIEW',
    MODIFY COLUMN `attempts` int NOT NULL DEFAULT 1 COMMENT '已执行或重试次数',
    MODIFY COLUMN `processed_count` int NOT NULL DEFAULT 0 COMMENT '本次成功处理数量',
    MODIFY COLUMN `error_message` varchar(1000) NULL DEFAULT NULL COMMENT '失败或驳回的安全错误摘要',
    MODIFY COLUMN `started_at` timestamp(6) NOT NULL COMMENT '任务开始时间',
    MODIFY COLUMN `finished_at` timestamp(6) NULL DEFAULT NULL COMMENT '任务结束时间',
    MODIFY COLUMN `next_attempt_at` timestamp(6) NULL DEFAULT NULL COMMENT '下次允许重试时间';

ALTER TABLE `search_history` COMMENT = '当前用户的个人搜索历史与筛选快照';
ALTER TABLE `search_history`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '搜索历史主键 UUID',
    MODIFY COLUMN `user_id` char(36) NOT NULL COMMENT '搜索历史所属用户 ID',
    MODIFY COLUMN `query_text` varchar(120) NOT NULL COMMENT '用户输入的原始查询文本',
    MODIFY COLUMN `normalized_query` varchar(120) NOT NULL COMMENT '规范化后的查询文本',
    MODIFY COLUMN `query_hash` char(64) NOT NULL COMMENT '查询文本与筛选条件的 SHA-256',
    MODIFY COLUMN `filters_json` varchar(2000) NOT NULL COMMENT '搜索筛选条件 JSON 快照',
    MODIFY COLUMN `result_count` bigint NOT NULL DEFAULT 0 COMMENT '最近一次搜索结果数量',
    MODIFY COLUMN `search_count` int NOT NULL DEFAULT 1 COMMENT '相同查询累计执行次数',
    MODIFY COLUMN `last_searched_at` timestamp(6) NOT NULL COMMENT '最近搜索时间',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间';

ALTER TABLE `search_index_job` COMMENT = 'OpenSearch 全量索引重建任务';
ALTER TABLE `search_index_job`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '索引重建任务主键 UUID',
    MODIFY COLUMN `requested_by` char(36) NULL DEFAULT NULL COMMENT '发起任务的管理员 ID',
    MODIFY COLUMN `index_name` varchar(120) NOT NULL COMMENT '目标 OpenSearch 索引名',
    MODIFY COLUMN `index_version` bigint NOT NULL COMMENT '查询副本版本号',
    MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING、RUNNING、COMPLETED 或 FAILED',
    MODIFY COLUMN `total_count` int NOT NULL DEFAULT 0 COMMENT '任务待处理总数',
    MODIFY COLUMN `indexed_count` int NOT NULL DEFAULT 0 COMMENT '成功写入索引的数量',
    MODIFY COLUMN `failed_count` int NOT NULL DEFAULT 0 COMMENT '处理失败的数量',
    MODIFY COLUMN `error_message` varchar(1000) NULL DEFAULT NULL COMMENT '失败或驳回的安全错误摘要',
    MODIFY COLUMN `started_at` timestamp(6) NULL DEFAULT NULL COMMENT '任务开始时间',
    MODIFY COLUMN `completed_at` timestamp(6) NULL DEFAULT NULL COMMENT '任务完成或失败时间',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '重建请求时间',
    MODIFY COLUMN `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '任务状态更新时间';

ALTER TABLE `user_notification` COMMENT = '评论、点赞、关注、审核及系统站内通知';
ALTER TABLE `user_notification`
    MODIFY COLUMN `id` char(36) NOT NULL COMMENT '站内通知主键 UUID',
    MODIFY COLUMN `recipient_id` char(36) NOT NULL COMMENT '接收用户 ID',
    MODIFY COLUMN `actor_id` char(36) NULL DEFAULT NULL COMMENT '触发通知的用户 ID；系统通知时为空',
    MODIFY COLUMN `notification_type` varchar(30) NOT NULL COMMENT '通知类型：COMMENT、LIKE、FOLLOW、MODERATION 或 SYSTEM',
    MODIFY COLUMN `target_type` varchar(30) NULL DEFAULT NULL COMMENT '通知关联目标类型，如 POST、COMMENT 或 USER',
    MODIFY COLUMN `target_id` char(36) NULL DEFAULT NULL COMMENT '通知关联业务目标 ID',
    MODIFY COLUMN `title` varchar(160) NOT NULL COMMENT '消息中心显示的短标题',
    MODIFY COLUMN `content` varchar(1000) NOT NULL COMMENT '通知正文摘要',
    MODIFY COLUMN `dedupe_key` varchar(180) NOT NULL COMMENT '同一接收人的稳定幂等键',
    MODIFY COLUMN `read_at` timestamp(6) NULL DEFAULT NULL COMMENT '读取时间；为空表示未读',
    MODIFY COLUMN `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';
