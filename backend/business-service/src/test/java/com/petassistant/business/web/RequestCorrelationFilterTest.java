package com.petassistant.business.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 验证业务服务直连和 Gateway 透传时都能得到安全请求编号。 */
class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void keepsSafeGatewayRequestIdAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER, "request-12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo("request-12345678");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesUnsafeValueThatCouldForgeLogLine() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER, "bad\nforged=true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER))
                .matches("[0-9a-f-]{36}")
                .doesNotContain("\n");
    }
}
