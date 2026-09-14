package com.dropit.global.storage;

import com.dropit.global.exception.ServiceException;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ImageServiceTest {

    private static final String BUCKET = "dropit-test";

    @Mock
    private S3Template s3Template;

    private S3ImageService s3ImageService;

    @BeforeEach
    void setUp() {
        s3ImageService = new S3ImageService(s3Template);
        ReflectionTestUtils.setField(s3ImageService, "bucket", BUCKET);
    }

    @Test
    @DisplayName("허용된 이미지 파일은 UUID 키와 MIME 타입 메타데이터로 업로드한다")
    void uploadImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "product.png", "image/png", new byte[]{1}
        );
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ObjectMetadata> metadataCaptor =
                ArgumentCaptor.forClass(ObjectMetadata.class);

        String key = s3ImageService.upload(file, "products/100");

        verify(s3Template).upload(
                eq(BUCKET),
                keyCaptor.capture(),
                any(InputStream.class),
                metadataCaptor.capture()
        );
        assertEquals(keyCaptor.getValue(), key);
        assertEquals("image/png", metadataCaptor.getValue().getContentType());
        assertTrue(key.matches("products/100/[\\w-]+\\.png"));
    }

    @Test
    @DisplayName("비어 있거나 지원하지 않는 파일은 S3 업로드 전에 거절한다")
    void rejectInvalidFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]
        );
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "note.txt", "text/plain", new byte[]{1}
        );

        assertErrorCode(StorageErrorCode.EMPTY_FILE,
                () -> s3ImageService.upload(emptyFile, "products/100"));
        assertErrorCode(StorageErrorCode.UNSUPPORTED_IMAGE_TYPE,
                () -> s3ImageService.upload(textFile, "products/100"));

        verifyNoInteractions(s3Template);
    }

    @Test
    @DisplayName("이미지 키는 응답용 10분 서명 URL로 변환한다")
    void createDownloadUrl() throws Exception {
        String key = "products/100/image.png";
        URL signedUrl = new URL("https://signed.example.com/image.png");
        when(s3Template.createSignedGetURL(
                BUCKET,
                key,
                Duration.ofMinutes(10)
        )).thenReturn(signedUrl);

        assertEquals(
                "https://signed.example.com/image.png",
                s3ImageService.createDownloadUrl(key)
        );
        assertNull(s3ImageService.createDownloadUrl(null));
        verify(s3Template).createSignedGetURL(
                BUCKET,
                key,
                Duration.ofMinutes(10)
        );
    }

    @Test
    @DisplayName("이미지 키를 공개 S3 URL로 변환한다")
    void createPublicUrl() throws Exception {
        String key = "products/100/image.png";
        URL publicUrl = new URL(
                "https://dropit-test.s3.ap-northeast-2.amazonaws.com/products/100/image.png"
        );
        S3Resource resource = org.mockito.Mockito.mock(S3Resource.class);
        when(s3Template.createResource(BUCKET, key)).thenReturn(resource);
        when(resource.getURL()).thenReturn(publicUrl);

        assertEquals(publicUrl.toString(), s3ImageService.createPublicUrl(key));
        assertNull(s3ImageService.createPublicUrl(null));
        verify(s3Template).createResource(BUCKET, key);
    }

    private void assertErrorCode(
            StorageErrorCode errorCode,
            org.junit.jupiter.api.function.Executable executable
    ) {
        ServiceException exception = assertThrows(ServiceException.class, executable);
        assertEquals(errorCode, exception.getErrorCode());
    }
}
