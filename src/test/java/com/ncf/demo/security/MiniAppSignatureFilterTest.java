package com.ncf.demo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncf.demo.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MiniAppSignatureFilterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private MiniAppSignatureFilter filter;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getMiniapp().setSignatureEnabled(true);
        filter = new MiniAppSignatureFilter(stringRedisTemplate, new ObjectMapper(), appProperties);
    }

    @Test
    void captchaEndpointBypassesMiniappSignatureCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/captcha");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void miniappAuthEndpointStillRequiresSignature() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Invalid miniapp signature");
        assertThat(chain.getRequest()).isNull();
    }
}
