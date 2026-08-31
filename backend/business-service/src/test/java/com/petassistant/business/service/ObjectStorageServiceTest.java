package com.petassistant.business.service;

import com.petassistant.business.config.MediaProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ObjectStorageServiceTest {

    @Test
    void shouldBuildPermanentPublicUrlAndEncodeEveryObjectKeySegment() {
        MediaProperties properties = new MediaProperties();
        properties.setPublicEndpoint("http://121.43.57.25:9000");
        properties.setBucket("pet-community");
        ObjectStorageService service = new ObjectStorageService(
                mock(MinioClient.class), mock(MinioClient.class), properties
        );

        assertThat(service.createPublicUrl("community/user 1/photo 1.jpg"))
                .isEqualTo("http://121.43.57.25:9000/pet-community/community/user%201/photo%201.jpg");
    }
}
