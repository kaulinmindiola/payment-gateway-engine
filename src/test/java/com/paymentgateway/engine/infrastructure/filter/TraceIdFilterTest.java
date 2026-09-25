package com.paymentgateway.engine.infrastructure.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    private String runAndCaptureMdc(MockHttpServletRequest request, MockHttpServletResponse response)
            throws ServletException, IOException {
        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> seenInsideChain.set(MDC.get(TraceContext.MDC_KEY)));
        return seenInsideChain.get();
    }

    @Test
    void withoutIncomingHeader_generatesTraceId_exposedInMdcAndResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = runAndCaptureMdc(new MockHttpServletRequest(), response);

        assertThat(seen).isNotBlank();
        assertThat(response.getHeader(TraceContext.HEADER)).isEqualTo(seen);
    }

    @Test
    void withValidIncomingHeader_respectsIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.HEADER, "client-trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(runAndCaptureMdc(request, response)).isEqualTo("client-trace-123");
        assertThat(response.getHeader(TraceContext.HEADER)).isEqualTo("client-trace-123");
    }

    @Test
    void withLogInjectionAttempt_replacesIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.HEADER, "abc\nFAKE LOG LINE level=ERROR");

        String seen = runAndCaptureMdc(request, new MockHttpServletResponse());

        assertThat(seen).doesNotContain("\n").doesNotContain("FAKE");
    }

    @Test
    void withOversizedHeader_replacesIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.HEADER, "a".repeat(65));

        assertThat(runAndCaptureMdc(request, new MockHttpServletResponse())).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void mdcIsCleared_evenWhenChainThrows() {
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> { throw new IllegalStateException("boom"); }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(TraceContext.MDC_KEY)).isNull();
    }
}