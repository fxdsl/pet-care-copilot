package com.petassistant.business.exception;

/**
 * FastAPI 健康、预处理或 RAG 调用失败时抛出的统一业务异常。
 */
public class AiServiceUnavailableException extends RuntimeException {

    /** 保留原始异常，方便在 Java 日志中定位网络或协议问题。 */
    public AiServiceUnavailableException(Throwable cause) {
        super("AI 服务暂时不可用", cause);
    }

    /** 异步任务可以保存不含正文的 HTTP 分类信息，避免把契约错误误报成宕机。 */
    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
