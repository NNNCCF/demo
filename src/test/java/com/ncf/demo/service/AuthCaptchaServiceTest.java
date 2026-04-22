package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthCaptchaServiceTest {

    @Test
    void decodeCaptchaImageSupportsDataUrl() {
        String payload = Base64.getEncoder().encodeToString("png-bytes".getBytes(StandardCharsets.UTF_8));

        byte[] decoded = AuthCaptchaService.decodeCaptchaImage("data:image/png;base64," + payload);

        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo("png-bytes");
    }

    @Test
    void decodeCaptchaImageRejectsInvalidBase64Payload() {
        assertThatThrownBy(() -> AuthCaptchaService.decodeCaptchaImage("data:image/png;base64,%%%"))
                .isInstanceOf(BizException.class);
    }
}
