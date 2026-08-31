package com.petassistant.business.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.petassistant.business.client.KnowledgeAiClient;
import com.petassistant.business.data.dto.internal.AiKnowledgePrecheckResponse;
import com.petassistant.business.data.dto.internal.CommunityPostView;
import com.petassistant.business.data.dto.request.CreateAdminKnowledgeSubmissionRequest;
import com.petassistant.business.data.dto.request.CreateCommunityKnowledgeSubmissionRequest;
import com.petassistant.business.data.dto.request.ReviewKnowledgeSubmissionRequest;
import com.petassistant.business.data.dto.response.KnowledgeReviewRecordResponse;
import com.petassistant.business.data.dto.response.KnowledgeSubmissionPageResponse;
import com.petassistant.business.data.dto.response.KnowledgeSubmissionResponse;
import com.petassistant.business.data.dto.response.KnowledgeSubmissionStatsResponse;
import com.petassistant.business.data.entity.KnowledgeReviewRecordEntity;
import com.petassistant.business.data.entity.KnowledgeSubmissionEntity;
import com.petassistant.business.data.entity.KnowledgeSubmissionVersionEntity;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.CommunityPostMapper;
import com.petassistant.business.data.mapper.KnowledgeMapper;
import com.petassistant.business.data.mapper.KnowledgeSubmissionMapper;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.exception.KnowledgeSubmissionConflictException;
import com.petassistant.business.exception.KnowledgeSubmissionNotFoundException;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第十一周知识共建状态机。MySQL 保存事实，Redis 只负责审核排序、缓存、去重提示和进度流。
 */
@Service
public class KnowledgeSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSubmissionService.class);
    private static final Set<String> FINAL_OR_RETRYABLE = Set.of("REJECTED", "WITHDRAWN", "FAILED");
    private static final Set<String> CATEGORIES = Set.of("FEEDING", "HEALTH", "VACCINE", "BEHAVIOR", "GROOMING", "OTHER");

    private final KnowledgeSubmissionMapper mapper;
    private final KnowledgeMapper knowledgeMapper;
    private final CommunityPostMapper postMapper;
    private final UserMapper userMapper;
    private final KnowledgeAiClient aiClient;
    private final KnowledgeService knowledgeService;
    private final OutboxService outboxService;
    private final MessageService messageService;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final PlatformMetricsService metrics;

    public KnowledgeSubmissionService(
            KnowledgeSubmissionMapper mapper,
            KnowledgeMapper knowledgeMapper,
            CommunityPostMapper postMapper,
            UserMapper userMapper,
            KnowledgeAiClient aiClient,
            KnowledgeService knowledgeService,
            OutboxService outboxService,
            MessageService messageService,
            StringRedisTemplate redisTemplate,
            RedissonClient redissonClient,
            PlatformMetricsService metrics
    ) {
        this.mapper = mapper;
        this.knowledgeMapper = knowledgeMapper;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.aiClient = aiClient;
        this.knowledgeService = knowledgeService;
        this.outboxService = outboxService;
        this.messageService = messageService;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.metrics = metrics;
    }

    /** 用户只能投稿自己已发布的帖子，且必须显式授权。 */
    @Transactional
    public KnowledgeSubmissionResponse submitCommunity(
            String userId, CreateCommunityKnowledgeSubmissionRequest request
    ) {
        //检查帖子是否存在且已发布
        //查询帖子信息并验证：
        //帖子必须存在
        //必须是当前用户自己的帖子（findOwnedView 会验证所有权）
        //状态必须是 PUBLISHED（已发布）
        CommunityPostView post = postMapper.findOwnedView(request.postId().trim(), userId);
        if (post == null || !"PUBLISHED".equals(post.status())) {
            throw new IllegalArgumentException("只能提交自己已发布的社区帖子");
        }
        //对用户输入的宠物类型和分类进行标准化处理
        String petType = normalizePetType(request.petType());
        String category = normalizeCategory(request.category());
        //避免重复提交投稿
        KnowledgeSubmissionEntity existing = mapper.findBySource("COMMUNITY_POST", post.id());
        Instant now = Instant.now();
        KnowledgeSubmissionEntity current;
        if (existing == null) {
            //首次提交投稿
            current = newSubmission(
                    "COMMUNITY_POST", // 来源类型
                    post.id(), // 来源ID（帖子ID）
                    userId, // 提交者
                    post.title(), // 标题
                    "宠里个宠社区",// 来源平台名称
                    post.authorDisplayName() == null ? post.authorUsername() : post.authorDisplayName(),
                    null,
                    null, // 外部链接、封面图
                    "TEXT", // 内容类型
                    petType,
                    category, // 宠物类型、分类
                    post.content(), // 正文内容
                    "GRANTED", // 授权状态（已授权）
                    null,
                    null, // 预检结果、审核备注
                now
            );
            mapper.insert(current);
        } else {
            //重新提交投稿,使用乐观锁确保数据一致性
            //检查当前投稿记录的状态是否在允许重提交的集合中
            //FINAL_OR_RETRYABLE 是一个预定义的状态集合（可能包含：APPROVED、REJECTED、WITHDRAWN 等终态）
            //如果状态是 PRECHECKING（预检中）或 REVIEWING（审核中）等中间状态，则拒绝重复提交
            if (!FINAL_OR_RETRYABLE.contains(existing.status())) {
                throw new KnowledgeSubmissionConflictException("该帖子已有进行中的知识投稿，不能重复提交");
            }
            //利用乐观锁确保数据一致性，避免并发更新导致的冲突
            int nextVersion = existing.currentVersion() + 1;
            //更新投稿记录的版本号和状态为 PRECHECKING（预检中）
            //AND current_version = #{currentVersion} 是乐观锁的核心，确保在更新时检查当前版本是否匹配
            if (mapper.resetForResubmission(
                    existing.id(), userId, post.title(), post.content(), petType, category, nextVersion, now
            ) == 0) {
                throw new KnowledgeSubmissionConflictException("投稿状态已变化，请刷新后重试");
            }
            //查询最新数据
            current = mapper.findById(existing.id());
        }
        //记录操作日志/时间线，用于后续的进度追踪
        recordSubmitted(current, userId, now);
        //重新查询最新的完整数据（包含生成的主键ID等）
        //转换为 DTO 返回给 Controller 层
        return toResponse(mapper.findById(current.id()), true);
    }

    /** 管理员资料也先进入预检和人工审核，不再使用“导入即生效”旧路径。 */
    @Transactional
    public KnowledgeSubmissionResponse submitAdminUpload(
            String adminId, CreateAdminKnowledgeSubmissionRequest request
    ) {
        String documentType = request.resolvedDocumentType();
        if (!Set.of("TEXT", "PDF").contains(documentType)) {
            throw new IllegalArgumentException("documentType 只允许 TEXT 或 PDF");
        }
        if ("PDF".equals(documentType)
                && (request.fileName() == null || !request.fileName().toLowerCase().endsWith(".pdf"))) {
            throw new IllegalArgumentException("PDF 投稿必须包含 .pdf 文件名");
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("有效期必须晚于当前时间");
        }
        Instant now = Instant.now();
        KnowledgeSubmissionEntity submission = newSubmission(
                "ADMIN_UPLOAD", null, adminId, request.title(), request.sourceName(), request.sourceAuthor(),
                request.sourceUrl(), request.fileName(), documentType, normalizePetType(request.petType()),
                normalizeCategory(request.category()), request.content(), "NOT_REQUIRED",
                request.sourcePublishedAt(), request.expiresAt(), now
        );
        mapper.insert(submission);
        recordSubmitted(submission, adminId, now);
        return toResponse(mapper.findById(submission.id()), true);
    }

    /** 写版本快照、时间线和 Outbox 必须与投稿主记录同事务提交。 */
    private void recordSubmitted(KnowledgeSubmissionEntity submission, String actorId, Instant now) {
        //写入版本快照
        //在版本历史表中插入一条新记录
        //快照机制：保存当前版本的标题和正文，即使原帖被修改，此版本的内容也不会变
        //为什么需要版本表？
        //场景：用户修改了帖子内容并重新提交
        //├─ 版本1：标题="如何给猫洗澡"，内容="第一步..."
        //├─ 版本2：标题="猫咪洗澡指南"（修改后），内容="更新后的步骤..."
        //└─ 可以回溯查看每个版本的具体内容
        mapper.insertVersion(new KnowledgeSubmissionVersionEntity(
                UUID.randomUUID().toString(), submission.id(), submission.currentVersion(), submission.title(),
                submission.originalContent(), null, null, null, null, null, null, now
        ));
        //记录操作日志/时间线，用于后续的进度追踪
        record(submission.id(), submission.currentVersion(), actorId, "SUBMITTED", null, "已提交预检");
        //发送领域事件（Outbox模式）,实现分布式事务。
        //什么是 Outbox 模式？
        //传统问题：
        //┌─────────────┐      ┌─────────────┐
        //│  数据库事务   │ ──→  │  发送消息    │  ❌ 如果消息发送失败，数据已提交但不一致！
        //│  (已提交)    │      │  (失败)     │
        //└─────────────┘      └─────────────┘
        //
        //Outbox 模式解决：
        //┌──────────────────────────────────────────┐
        //│          同一个数据库事务                   │
        //│  ┌──────────┐    ┌──────────────────┐     │
        //│  │ 业务数据   │ +  │ Outbox 表记录     │     │
        //│  │ (INSERT)  │    │ (INSERT)         │     │
        //│  └──────────┘    └──────────────────┘     │
        //└──────────────────────────────────────────┘
        //                        ↓
        //              后台定时任务扫描 Outbox 表
        //                        ↓
        //              发送到消息队列 / 调用外部服务
        //                        ↓
        //              标记为已发送（防止重复）
        outboxService.record("KNOWLEDGE_SUBMISSION", submission.id(), "KNOWLEDGE_PRECHECK_REQUESTED", actorId);
        //清除该投稿相关的缓存数据
        //保证下次查询时从数据库读取最新状态
        evictCache(submission.id());
    }

    /** RabbitMQ 消费者调用的幂等预检；重复消息无法越过 PRECHECKING 条件。 */
    @Transactional
    public void processPrecheck(String submissionId) {
        // 1. 查询知识提交记录
        KnowledgeSubmissionEntity submission = requireSubmission(submissionId);
        // 2. 检查状态机条件（必须是 PRECHECKING 状态才能预检）
        //检查当前状态是否为 "PRECHECKING"（预检中）
        //如果不是 PRECHECKING 状态 → 立即返回，不做任何处理
        //如果是 PRECHECKING 状态 → 继续执行后续逻辑
        if (!"PRECHECKING".equals(submission.status())) return;
        //3. 调用 AI 预检服务进行风险评估
        AiKnowledgePrecheckResponse result = aiClient.precheck(
                submission.title(), submission.originalContent(), submission.sourceType()
        );
        //拼接风险标签
        String labels = String.join(",", result.riskLabels());
        //完成预检，更新知识提交记录的状态为 PENDING_REVIEW（待审核中）
        //使用乐观锁
        //什么时候返回 0？
        //
        //版本号不匹配：其他事务已经更新了这条记录（并发冲突）
        //状态不是 PRECHECKING：已经被其他流程处理过了
        //记录不存在：ID 无效（理论上不可能，因为前面已经 require 过了）
        if (mapper.completePrecheck(
                submission.id(), submission.currentVersion(), result.cleanedContent(), result.checksum(),
                result.summary(), result.riskLevel(), labels, result.qualityScore(), Instant.now()
        ) == 0) return;
        //更新版本历史表
        //为什么要保留版本历史？
        //
        //审计追踪：可以看到每次修改的内容变化
        //回滚能力：如果发现 AI 审核有误，可以回退到之前的版本
        //数据分析：分析内容质量和风险趋势
        //去重检测：通过 checksum 判断是否与已有内容重复
        mapper.updateVersionPrecheck(
                submission.id(), submission.currentVersion(), result.cleanedContent(), result.checksum(),
                result.summary(), result.riskLevel(), labels, result.qualityScore()
        );
        //记录预检完成事件，更新knowledge_review_record表。
        record(submission.id(), submission.currentVersion(), null, "PRECHECK_COMPLETED", null,
                "预检完成：" + result.riskLevel());
        //将投稿添加到审核队列,用zset存储。
        addReviewQueue(submission.id(), result.riskLevel(), submission.createdAt());
        //更新去重布隆过滤器
        updateChecksumBloom(result.checksum());
        //更新缓存
        writeSubmissionCache(mapper.findById(submission.id()));
    }

    /** 预检或发布异常被记录为可观察失败，消息可安全重试但不会重复发布。 */
    @Transactional
    public void markFailed(String submissionId, String message) {
        KnowledgeSubmissionEntity submission = mapper.findById(submissionId);
        if (submission == null) return;
        String safeMessage = message == null ? "异步处理失败" : message.substring(0, Math.min(message.length(), 900));
        if (mapper.markFailed(submission.id(), submission.currentVersion(), safeMessage, Instant.now()) > 0) {
            record(submission.id(), submission.currentVersion(), null, "FAILED", null, safeMessage);
            removeReviewQueue(submission.id());
            evictCache(submission.id());
        }
    }

    /** 审核批准后只进入 PUBLISHING，向量化和发布由 RabbitMQ 异步完成。 */
    @Transactional
    public KnowledgeSubmissionResponse review(
            String adminId, String submissionId, ReviewKnowledgeSubmissionRequest request
    ) {
        KnowledgeSubmissionEntity submission = requireSubmission(submissionId);
        String action = request.action().trim().toUpperCase();
        if (submission.currentVersion() != request.expectedVersion()) {
            throw new KnowledgeSubmissionConflictException("投稿版本已变化，请重新查看内容后审核");
        }
        Instant now = Instant.now();
        if ("REJECT".equals(action)) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new IllegalArgumentException("驳回时必须填写原因");
            }
            if (mapper.reject(submission.id(), request.expectedVersion(), adminId, request.reason().trim(), now) == 0) {
                throw new KnowledgeSubmissionConflictException("投稿已被其他管理员处理");
            }
            record(submission.id(), submission.currentVersion(), adminId, "REJECTED", null, request.reason().trim());
            notifyAuthor(submission, adminId, "知识投稿未通过", request.reason().trim());
            removeReviewQueue(submission.id());
        } else if ("APPROVE".equals(action)) {
            String trustLevel = resolveTrustLevel(submission, request.trustLevel());
            validateApproval(submission, trustLevel);
            if (mapper.approve(submission.id(), request.expectedVersion(), adminId, now) == 0) {
                throw new KnowledgeSubmissionConflictException("投稿已被其他管理员处理");
            }
            record(submission.id(), submission.currentVersion(), adminId, "APPROVED", trustLevel,
                    blankOr(request.reason(), "人工审核通过"));
            outboxService.record("KNOWLEDGE_SUBMISSION", submission.id(), "KNOWLEDGE_PUBLISH_REQUESTED", adminId);
            removeReviewQueue(submission.id());
        } else {
            throw new IllegalArgumentException("action 只允许 APPROVE 或 REJECT");
        }
        evictCache(submission.id());
        // 历史或测试数据可能没有创建时间；指标缺失不能阻断真实审核事务。
        if (submission.createdAt() != null) {
            metrics.recordKnowledgeReviewLatency(java.time.Duration.between(submission.createdAt(), now));
        }
        return toResponse(mapper.findById(submission.id()), true);
    }

    /** Redisson 分布式锁防止多实例重复向量化和重复发布同一版本。 */
    @Transactional
    public void processPublish(String submissionId) {
        RLock lock = redissonClient.getLock("knowledge:publish:" + submissionId);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 90, TimeUnit.SECONDS);
            if (!locked) return;
            KnowledgeSubmissionEntity submission = requireSubmission(submissionId);
            if (!"PUBLISHING".equals(submission.status())) return;
            String trustLevel = latestTrustLevel(submission.id());
            String documentId = knowledgeService.publishApproved(submission, trustLevel);
            Instant now = Instant.now();
            if (mapper.markPublished(submission.id(), submission.currentVersion(), documentId, now) == 0) {
                throw new KnowledgeSubmissionConflictException("发布状态已变化");
            }
            outboxService.record("SEARCH_DOCUMENT", documentId, "SEARCH_KNOWLEDGE_UPSERT", submission.reviewerUserId());
            record(submission.id(), submission.currentVersion(), submission.reviewerUserId(),
                    "PUBLISHED", trustLevel, "已生成分块和向量并进入 RAG");
            notifyAuthor(submission, submission.reviewerUserId(), "知识投稿已发布", "资料已进入智能问答知识库");
            writeStream("PUBLISHED", submission.id(), documentId, submission.currentVersion());
            writeDocumentCache(documentId, submission);
            writeSubmissionCache(mapper.findById(submission.id()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待知识发布锁时被中断", interrupted);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    /** 作者可撤回自己的投稿；READY 文档同步失效，后续检索 SQL 也会再次校验。 */
    @Transactional
    public KnowledgeSubmissionResponse withdraw(String userId, String submissionId) {
        KnowledgeSubmissionEntity submission = requireOwned(userId, submissionId);
        if (mapper.withdrawOwned(submission.id(), userId, Instant.now()) == 0) {
            throw new KnowledgeSubmissionConflictException("当前状态不能撤回，请刷新后重试");
        }
        knowledgeService.withdrawPublished(submission.id());
        record(submission.id(), submission.currentVersion(), userId, "WITHDRAWN", null, "作者撤回授权");
        removeReviewQueue(submission.id());
        if (submission.publishedDocumentId() != null) {
            redisTemplate.delete("knowledge:document:" + submission.publishedDocumentId());
            outboxService.record(
                    "SEARCH_DOCUMENT", submission.publishedDocumentId(), "SEARCH_KNOWLEDGE_DELETE", userId
            );
        }
        writeStream("WITHDRAWN", submission.id(), submission.publishedDocumentId(), submission.currentVersion());
        evictCache(submission.id());
        return toResponse(mapper.findById(submission.id()), true);
    }

    @Transactional(readOnly = true)
    public KnowledgeSubmissionPageResponse mine(String userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        return new KnowledgeSubmissionPageResponse(
                mapper.findMine(userId, safePage * safeSize, safeSize).stream()
                        // 列表不逐条查询时间线，避免投稿数量增加后出现 N+1 SQL。
                        .map(item -> toResponse(item, false)).toList(),
                safePage, safeSize, mapper.countMine(userId)
        );
    }

    @Transactional(readOnly = true)
    public KnowledgeSubmissionResponse ownedDetail(String userId, String submissionId) {
        return toResponse(requireOwned(userId, submissionId), true);
    }

    @Transactional(readOnly = true)
    public KnowledgeSubmissionPageResponse reviewPage(
            String status, String riskLevel, String sourceType, int page, int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String normalizedStatus = upperOrNull(status);
        String normalizedRisk = upperOrNull(riskLevel);
        String normalizedSource = upperOrNull(sourceType);
        return new KnowledgeSubmissionPageResponse(
                mapper.findReviewPage(normalizedStatus, normalizedRisk, normalizedSource,
                                safePage * safeSize, safeSize).stream()
                        .map(item -> toResponse(item, false)).toList(),
                safePage, safeSize,
                mapper.countReviewPage(normalizedStatus, normalizedRisk, normalizedSource)
        );
    }

    @Transactional(readOnly = true)
    public KnowledgeSubmissionResponse adminDetail(String submissionId) {
        return toResponse(requireSubmission(submissionId), true);
    }

    @Transactional(readOnly = true)
    public KnowledgeSubmissionStatsResponse stats() {
        return new KnowledgeSubmissionStatsResponse(
                mapper.countByStatus("PENDING_REVIEW"), mapper.countByStatus("PUBLISHED"),
                mapper.countByStatus("REJECTED"), mapper.countHighRisk()
        );
    }

    private KnowledgeSubmissionEntity newSubmission(
            String sourceType, String sourceBusinessId, String authorId, String title, String sourceName,
            String sourceAuthor, String sourceUrl, String fileName, String documentType, String petType,
            String category, String content, String consent, Instant sourcePublishedAt, Instant expiresAt, Instant now
    ) {
        return new KnowledgeSubmissionEntity(
                UUID.randomUUID().toString(), sourceType, sourceBusinessId, authorId, title.trim(),
                trimToNull(sourceName), trimToNull(sourceAuthor), trimToNull(sourceUrl), trimToNull(fileName),
                documentType, petType, category, content.trim(), null, null, consent, "PRECHECKING",
                null, null, null, null, 1, null, null, null, sourcePublishedAt, null, expiresAt,
                null, now, now
        );
    }

    private KnowledgeSubmissionEntity requireSubmission(String id) {
        KnowledgeSubmissionEntity submission = mapper.findById(id);
        if (submission == null) throw new KnowledgeSubmissionNotFoundException();
        return submission;
    }

    private KnowledgeSubmissionEntity requireOwned(String userId, String id) {
        KnowledgeSubmissionEntity submission = requireSubmission(id);
        if (!userId.equals(submission.authorUserId())) throw new KnowledgeSubmissionNotFoundException();
        return submission;
    }

    private void validateApproval(KnowledgeSubmissionEntity submission, String trustLevel) {
        if ("HIGH".equals(submission.riskLevel()) && !"A".equals(trustLevel)) {
            throw new IllegalArgumentException("高风险内容只能以 A 级可信来源批准");
        }
        if (Set.of("HEALTH", "VACCINE").contains(submission.category()) && "C".equals(trustLevel)) {
            throw new IllegalArgumentException("健康和疫苗知识不能以普通用户经验 C 级进入 RAG");
        }
    }

    private String resolveTrustLevel(KnowledgeSubmissionEntity submission, String requested) {
        if ("COMMUNITY_POST".equals(submission.sourceType())) return "C";
        String value = requested == null || requested.isBlank() ? "B" : requested.trim().toUpperCase();
        if (!Set.of("A", "B").contains(value)) throw new IllegalArgumentException("管理员资料信任等级只允许 A 或 B");
        return value;
    }

    private String latestTrustLevel(String submissionId) {
        return mapper.findTimeline(submissionId).stream()
                .filter(item -> "APPROVED".equals(item.action()) && item.trustLevel() != null)
                .reduce((first, second) -> second).map(KnowledgeReviewRecordEntity::trustLevel).orElse("C");
    }

    private void record(
            String submissionId, int version, String reviewerId, String action, String trustLevel, String reason
    ) {
        mapper.insertReviewRecord(new KnowledgeReviewRecordEntity(
                UUID.randomUUID().toString(), submissionId, version, reviewerId,
                action, trustLevel, reason, Instant.now()
        ));
    }

    private KnowledgeSubmissionResponse toResponse(KnowledgeSubmissionEntity item, boolean withTimeline) {
        UserEntity author = item.authorUserId() == null ? null : userMapper.findById(item.authorUserId());
        UserEntity reviewer = item.reviewerUserId() == null ? null : userMapper.findById(item.reviewerUserId());
        List<KnowledgeReviewRecordResponse> timeline = withTimeline
                ? mapper.findTimeline(item.id()).stream().map(record -> {
                    UserEntity user = record.reviewerUserId() == null ? null : userMapper.findById(record.reviewerUserId());
                    return new KnowledgeReviewRecordResponse(
                            record.id(), record.version(), record.reviewerUserId(), userName(user), record.action(),
                            record.trustLevel(), record.reason(), record.createdAt()
                    );
                }).toList()
                : List.of();
        return new KnowledgeSubmissionResponse(
                item.id(), item.sourceType(), item.sourceBusinessId(), item.authorUserId(), userName(author),
                item.title(), item.sourceName(), item.sourceAuthor(), item.sourceUrl(), item.fileName(),
                item.documentType(), item.petType(), item.category(), item.originalContent(), item.cleanedContent(),
                item.consentStatus(), item.status(), item.riskLevel(), splitLabels(item.riskLabels()),
                item.aiSummary(), item.qualityScore(), item.currentVersion(), item.reviewerUserId(), userName(reviewer),
                item.publishedDocumentId(), item.sourcePublishedAt(), item.reviewedAt(), item.publishedAt(),
                item.expiresAt(), item.errorMessage(), item.createdAt(), item.updatedAt(), timeline
        );
    }

    private void notifyAuthor(KnowledgeSubmissionEntity submission, String actorId, String title, String content) {
        if (submission.authorUserId() != null) {
            messageService.createNotification(
                    submission.authorUserId(), actorId, "SYSTEM", "KNOWLEDGE_SUBMISSION", submission.id(),
                    title, content, "knowledge:" + submission.id() + ":" + submission.currentVersion() + ":" + title
            );
        }
    }
    /**id	String	知识提交ID	"sub_abc123"
     riskLevel	String	AI 预检的风险等级	"HIGH", "MEDIUM", "LOW", "CRITICAL"
     createdAt	Instant	知识提交的创建时间	2026-08-31T09:00:00Z*/

    private void addReviewQueue(String id, String riskLevel, Instant createdAt) {
        try {
            //基于风险等级计算优先级分数
            //关键点：数值间隔要足够大,因为需要加上创建时间戳,避免与创建时间戳冲突
            double priority = switch (riskLevel)
            { case "HIGH" -> 0;
                case "MEDIUM" -> 1_000_000_000_000d;
                default -> 2_000_000_000_000d; };
            //添加到 Redis 有序集合
            redisTemplate.opsForZSet().add("knowledge:review:queue",
                id,
                priority + createdAt.toEpochMilli());
        } catch (DataAccessException error) {
            log.debug("Knowledge review queue unavailable: {}", error.toString());
        }
    }

    private void removeReviewQueue(String id) {
        try { redisTemplate.opsForZSet().remove("knowledge:review:queue", id); }
        catch (DataAccessException error) { log.debug("Knowledge review queue cleanup skipped: {}", error.toString()); }
    }

    private void updateChecksumBloom(String checksum) {
        try {
            RBloomFilter<String> bloom = redissonClient.getBloomFilter("knowledge:checksum:bloom");
            bloom.tryInit(100_000, 0.01);
            bloom.add(checksum);
        } catch (RuntimeException error) {
            log.debug("Knowledge checksum bloom unavailable: {}", error.toString());
        }
    }

    private void writeSubmissionCache(KnowledgeSubmissionEntity submission) {
        try {
            String key = "knowledge:submission:" + submission.id();
            redisTemplate.opsForHash().putAll(key, Map.of(
                    "status", submission.status(), "version", Integer.toString(submission.currentVersion()),
                    "riskLevel", blankOr(submission.riskLevel(), "UNKNOWN"),
                    "updatedAt", submission.updatedAt().toString()
            ));
            redisTemplate.expire(key, Duration.ofMinutes(15));
        } catch (DataAccessException error) {
            log.debug("Knowledge submission cache write skipped: {}", error.toString());
        }
    }

    private void evictCache(String submissionId) {
        try { redisTemplate.delete("knowledge:submission:" + submissionId); }
        catch (DataAccessException error) { log.debug("Knowledge submission cache eviction skipped: {}", error.toString()); }
    }

    private void writeStream(String type, String submissionId, String documentId, int version) {
        try {
            redisTemplate.opsForStream().add(StreamRecords.newRecord()
                    .in("knowledge:index:events")
                    .ofMap(Map.of(
                            "type", type, "submissionId", submissionId,
                            "documentId", documentId == null ? "" : documentId,
                            "version", Integer.toString(version), "occurredAt", Instant.now().toString()
                    )));
            redisTemplate.opsForStream().trim("knowledge:index:events", 10_000, true);
        } catch (DataAccessException error) {
            log.debug("Knowledge index progress stream unavailable: {}", error.toString());
        }
    }

    /** 已发布文档摘要只作展示缓存，正文和检索资格始终回源 MySQL。 */
    private void writeDocumentCache(String documentId, KnowledgeSubmissionEntity submission) {
        try {
            String value = "{\"status\":\"READY\",\"submissionId\":\"" + submission.id()
                    + "\",\"version\":" + submission.currentVersion() + "}";
            redisTemplate.opsForValue().set("knowledge:document:" + documentId, value, Duration.ofHours(6));
        } catch (DataAccessException error) {
            log.debug("Knowledge document cache write skipped: {}", error.toString());
        }
    }

    private static String normalizePetType(String value) {
        String normalized = value.trim().toUpperCase();
        if (!Set.of("CAT", "DOG", "OTHER").contains(normalized)) throw new IllegalArgumentException("宠物类型无效");
        return normalized;
    }

    private static String normalizeCategory(String value) {
        String normalized = value.trim().toUpperCase();
        if (!CATEGORIES.contains(normalized)) throw new IllegalArgumentException("知识分类无效");
        return normalized;
    }

    private static String userName(UserEntity user) {
        if (user == null) return null;
        return user.displayName() == null || user.displayName().isBlank() ? user.username() : user.displayName();
    }

    private static List<String> splitLabels(String labels) {
        return labels == null || labels.isBlank() ? List.of() : Arrays.asList(labels.split(","));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String upperOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
