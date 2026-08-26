package com.petassistant.business.service;

import com.petassistant.business.config.MediaProperties;
import com.petassistant.business.data.dto.request.CreateMediaUploadRequest;
import com.petassistant.business.data.mapper.CommunityMediaMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 媒体白名单在生成 MinIO 地址前生效。 */
@ExtendWith(MockitoExtension.class)
class CommunityMediaServiceTest {

    @Mock CommunityMediaMapper mapper;
    @Mock ObjectStorageService objectStorage;
    @Mock OutboxService outboxService;

    @Test
    void shouldRejectExecutableContentType() {
        CommunityMediaService service = new CommunityMediaService(
                mapper, objectStorage, new MediaProperties(), outboxService
        );

        assertThatThrownBy(() -> service.createUpload("user-1", new CreateMediaUploadRequest(
                "danger.exe", "application/x-msdownload", 1024, null
        ))).isInstanceOf(IllegalArgumentException.class);

        verify(objectStorage, never()).createUploadUrl(org.mockito.ArgumentMatchers.any());
    }
}
