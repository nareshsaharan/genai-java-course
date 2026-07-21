package com.studybuddy.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesRequestIdWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] mdcValueDuringRequest = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcValueDuringRequest[0] = MDC.get("requestId");
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(mdcValueDuringRequest[0]).isNotBlank();
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(mdcValueDuringRequest[0]);
    }

    @Test
    void reusesIncomingRequestIdHeader() throws Exception {
        String incomingId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", incomingId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] mdcValueDuringRequest = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcValueDuringRequest[0] = MDC.get("requestId");
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(mdcValueDuringRequest[0]).isEqualTo(incomingId);
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(incomingId);
    }

    @Test
    void clearsMdcAfterRequestCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(MDC.get("requestId")).isNull();
    }
}
