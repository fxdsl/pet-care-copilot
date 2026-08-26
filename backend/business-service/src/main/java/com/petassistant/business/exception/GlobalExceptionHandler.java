package com.petassistant.business.exception;

import java.time.Instant;

import com.petassistant.business.data.dto.response.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常转换器，将内部异常稳定地转换为前端可处理的错误契约。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 返回第一个字段校验错误，避免把框架异常细节暴露给浏览器。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().isEmpty()
                ? "请求参数无效"
                : exception.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return new ApiError("INVALID_REQUEST", message, Instant.now());
    }

    /** 会话不存在时使用 404，而不是返回空对象。 */
    @ExceptionHandler(ConversationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleConversationNotFound(ConversationNotFoundException exception) {
        return new ApiError("CONVERSATION_NOT_FOUND", exception.getMessage(), Instant.now());
    }

    /** 宠物档案不存在时返回 404，提醒前端刷新档案列表。 */
    @ExceptionHandler(PetProfileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handlePetProfileNotFound(PetProfileNotFoundException exception) {
        return new ApiError("PET_PROFILE_NOT_FOUND", exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(CommunityPostNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleCommunityPostNotFound(CommunityPostNotFoundException exception) {
        return new ApiError("COMMUNITY_POST_NOT_FOUND", exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(CommunityMediaNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleCommunityMediaNotFound(CommunityMediaNotFoundException exception) {
        return new ApiError("COMMUNITY_MEDIA_NOT_FOUND", exception.getMessage(), Instant.now());
    }

    /** 管理操作的目标不存在属于资源问题，不应让前端误以为当前管理员登录失效。 */
    @ExceptionHandler(AdminUserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleAdminUserNotFound(AdminUserNotFoundException exception) {
        return new ApiError("ADMIN_USER_NOT_FOUND", exception.getMessage(), Instant.now());
    }

    /** 登录凭证失败统一返回 401，避免区分用户名、密码或刷新令牌错误。 */
    @ExceptionHandler(AuthenticationFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiError handleAuthenticationFailed(AuthenticationFailedException exception) {
        return new ApiError("AUTHENTICATION_FAILED", exception.getMessage(), Instant.now());
    }

    /** JWT 对应用户不存在或已停用。 */
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiError handleUserNotFound(UserNotFoundException exception) {
        return new ApiError("USER_UNAVAILABLE", exception.getMessage(), Instant.now());
    }

    /** 用户名唯一索引冲突。 */
    @ExceptionHandler(UsernameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleUsernameExists(UsernameAlreadyExistsException exception) {
        return new ApiError("USERNAME_ALREADY_EXISTS", exception.getMessage(), Instant.now());
    }

    /** 防止撤销最后管理员或管理员禁用自身导致系统失去治理入口。 */
    @ExceptionHandler(AdminOperationConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleAdminConflict(AdminOperationConflictException exception) {
        return new ApiError("ADMIN_OPERATION_CONFLICT", exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(CommunityPostConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handlePostConflict(CommunityPostConflictException exception) {
        return new ApiError("COMMUNITY_POST_CONFLICT", exception.getMessage(), Instant.now());
    }

    /** 重复举报和并发审核使用稳定的 409 契约。 */
    @ExceptionHandler(CommunityInteractionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleCommunityInteractionConflict(CommunityInteractionConflictException exception) {
        return new ApiError("COMMUNITY_INTERACTION_CONFLICT", exception.getMessage(), Instant.now());
    }

    /** 投稿不存在或无权查看时不暴露其他用户的待审正文。 */
    @ExceptionHandler(KnowledgeSubmissionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleKnowledgeSubmissionNotFound(KnowledgeSubmissionNotFoundException exception) {
        return new ApiError("KNOWLEDGE_SUBMISSION_NOT_FOUND", exception.getMessage(), Instant.now());
    }

    /** 并发审核、重复投稿或状态不允许时返回 409。 */
    @ExceptionHandler(KnowledgeSubmissionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleKnowledgeSubmissionConflict(KnowledgeSubmissionConflictException exception) {
        return new ApiError("KNOWLEDGE_SUBMISSION_CONFLICT", exception.getMessage(), Instant.now());
    }

    /** Redis Lua 登录保护命中上限。 */
    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    ApiError handleTooManyRequests(TooManyRequestsException exception) {
        return new ApiError("TOO_MANY_REQUESTS", exception.getMessage(), Instant.now());
    }

    /** 刷新令牌必须依赖 Redis，故障时返回 503 而不是伪造会话。 */
    @ExceptionHandler(AuthSessionUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiError handleAuthSessionUnavailable(AuthSessionUnavailableException exception) {
        log.warn("Authentication session storage unavailable", exception);
        return new ApiError("AUTH_SESSION_UNAVAILABLE", "登录会话服务暂时不可用，请确认 Redis 已启动", Instant.now());
    }

    /** 处理分块范围等业务参数错误。 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleIllegalArgument(IllegalArgumentException exception) {
        return new ApiError("INVALID_REQUEST", exception.getMessage(), Instant.now());
    }

    /** JSON 缺失、类型不匹配或语法错误统一返回可理解的 400。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleUnreadableBody() {
        return new ApiError("INVALID_JSON", "请求内容格式不正确，请检查填写的数据", Instant.now());
    }

    /** FastAPI 不可用时返回 502，明确区分业务层自身错误。 */
    @ExceptionHandler(AiServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    ApiError handleAiServiceUnavailable() {
        return new ApiError("AI_SERVICE_UNAVAILABLE", "AI 服务暂时不可用，请稍后重试", Instant.now());
    }

    /** MinIO 故障与 AI 故障分开返回，便于前端给出准确启动提示。 */
    @ExceptionHandler(MediaStorageUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiError handleMediaStorageUnavailable(MediaStorageUnavailableException exception) {
        log.warn("Media storage unavailable", exception);
        return new ApiError("MEDIA_STORAGE_UNAVAILABLE", "媒体存储暂时不可用，请确认 MinIO 已启动", Instant.now());
    }

    /** 数据库暂时不可用时不暴露连接串或 SQL 细节。 */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiError handleDataAccess(DataAccessException exception) {
        log.error("Database operation failed", exception);
        return new ApiError("DATABASE_UNAVAILABLE", "数据库暂时不可用，请确认 MySQL 已启动", Instant.now());
    }

    /** 捕获未预期异常并记录完整日志，对浏览器只返回稳定错误契约。 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiError handleUnexpected(Exception exception) {
        log.error("Unexpected business service error", exception);
        return new ApiError("INTERNAL_ERROR", "服务处理失败，请查看 Java 控制台日志", Instant.now());
    }
}
