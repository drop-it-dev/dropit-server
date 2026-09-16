package com.dropit.notification.email.sender;

import com.dropit.notification.email.messaging.EmailSqsProperties;
import io.awspring.cloud.autoconfigure.ses.SesClientCustomizer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseEmailSesClientConfigTest {

    private final PurchaseEmailSesClientConfig config = new PurchaseEmailSesClientConfig();

    @Test
    void SES_전체호출과_개별시도_timeout을_설정한다() {
        SesClientCustomizer customizer = config.purchaseEmailSesClientCustomizer(
                new EmailSesProperties("no-reply@dropit.example", 20_000, 5_000),
                sqsProperties(60)
        );
        SesClientBuilder builder = SesClient.builder();

        customizer.customize(builder);

        ClientOverrideConfiguration overrideConfiguration =
                builder.overrideConfiguration();
        assertThat(overrideConfiguration.apiCallTimeout())
                .contains(Duration.ofSeconds(20));
        assertThat(overrideConfiguration.apiCallAttemptTimeout())
                .contains(Duration.ofSeconds(5));
    }

    @Test
    void Parameter_Store가_전체호출_timeout을_늘려도_허용하지_않는다() {
        EmailSesProperties properties = new EmailSesProperties(
                "no-reply@dropit.example",
                60_000,
                5_000
        );

        assertThatThrownBy(() -> config.purchaseEmailSesClientCustomizer(
                properties,
                sqsProperties(60)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("api-call-timeout-millis");
    }

    @Test
    void 전체호출_timeout은_visibility_timeout보다_짧아야_한다() {
        EmailSesProperties properties = new EmailSesProperties(
                "no-reply@dropit.example",
                10_000,
                5_000
        );

        assertThatThrownBy(() -> config.purchaseEmailSesClientCustomizer(
                properties,
                sqsProperties(10)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumer-visibility-timeout-seconds");
    }

    @Test
    void visibility_timeout은_SES호출후_SENT저장과_ACK시간을_남겨야_한다() {
        EmailSesProperties properties = new EmailSesProperties(
                "no-reply@dropit.example",
                20_000,
                5_000
        );

        assertThatThrownBy(() -> config.purchaseEmailSesClientCustomizer(
                properties,
                sqsProperties(21)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumer-visibility-timeout-seconds")
                .hasMessageContaining("markSent retries")
                .hasMessageContaining("manual acknowledgement");
    }

    private EmailSqsProperties sqsProperties(int visibilityTimeoutSeconds) {
        return new EmailSqsProperties(
                "queue-url",
                true,
                3_000,
                50,
                30_000,
                5_000,
                true,
                2,
                2,
                10,
                visibilityTimeoutSeconds
        );
    }
}
