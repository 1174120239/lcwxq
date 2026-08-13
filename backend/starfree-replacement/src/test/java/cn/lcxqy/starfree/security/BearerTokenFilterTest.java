package cn.lcxqy.starfree.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenFilterTest {
    @Test
    void bearerHeaderBecomesTheTokenParameter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/SFreeUsers/userStatus");
        request.addHeader("Authorization", "Bearer sf2_abc");
        AtomicReference<HttpServletRequest> filtered = new AtomicReference<>();

        new BearerTokenFilter().doFilter(request, new MockHttpServletResponse(),
                (incoming, response) -> filtered.set((HttpServletRequest) incoming));

        assertThat(filtered.get().getParameter("token")).isEqualTo("sf2_abc");
        assertThat(filtered.get().getParameterMap().get("token")).containsExactly("sf2_abc");
        assertThat(filtered.get().getAttribute(BearerTokenFilter.BEARER_ONLY_ATTRIBUTE))
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    void legacyParameterRemainsWhenThereIsNoBearerHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/SFreeUsers/userStatus");
        request.setParameter("token", "legacy-parameter");
        AtomicReference<HttpServletRequest> filtered = new AtomicReference<>();

        new BearerTokenFilter().doFilter(request, new MockHttpServletResponse(),
                (incoming, response) -> filtered.set((HttpServletRequest) incoming));

        assertThat(filtered.get().getParameter("token")).isEqualTo("legacy-parameter");
    }

    @Test
    void conflictingBearerAndParameterAreRejectedBeforeProxying() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload/full");
        request.addHeader("Authorization", "Bearer sf2_header");
        request.setParameter("token", "sf2_body");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletRequest> filtered = new AtomicReference<>();

        new BearerTokenFilter().doFilter(request, response,
                (incoming, outgoing) -> filtered.set((HttpServletRequest) incoming));

        assertThat(filtered.get()).isNull();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("Token参数冲突");
    }

    @Test
    void duplicateTokenParametersAreRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/SFreeUsers/userStatus");
        request.setParameter("token", "sf2_first", "sf2_second");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletRequest> filtered = new AtomicReference<>();

        new BearerTokenFilter().doFilter(request, response,
                (incoming, outgoing) -> filtered.set((HttpServletRequest) incoming));

        assertThat(filtered.get()).isNull();
        assertThat(response.getStatus()).isEqualTo(400);
    }
}
