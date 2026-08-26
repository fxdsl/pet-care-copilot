package com.petassistant.business.exception;

/** MinIO 不可连接或对象元数据不符合预期。 */
public class MediaStorageUnavailableException extends RuntimeException {

    public MediaStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
