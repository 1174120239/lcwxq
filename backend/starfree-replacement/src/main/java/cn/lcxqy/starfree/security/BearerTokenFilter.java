package cn.lcxqy.starfree.security;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adds Authorization: Bearer support while legacy clients still send the token parameter. */
@Component
public class BearerTokenFilter extends OncePerRequestFilter {
    public static final String BEARER_ONLY_ATTRIBUTE =
            BearerTokenFilter.class.getName() + ".bearerOnly";
    private static final String BEARER = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = bearerToken(request.getHeader("Authorization"));
        String[] parameterTokens = request.getParameterValues("token");
        if (parameterTokens != null
                && (parameterTokens.length != 1
                || token != null && !token.equals(parameterTokens[0]))) {
            rejectAmbiguousToken(response);
            return;
        }
        if (token != null && parameterTokens == null) {
            request.setAttribute(BEARER_ONLY_ATTRIBUTE, Boolean.TRUE);
        }
        chain.doFilter(token == null ? request : new TokenRequest(request, token), response);
    }

    private void rejectAmbiguousToken(HttpServletResponse response) throws IOException {
        byte[] body = "{\"code\":0,\"msg\":\"Token参数冲突\"}"
                .getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private String bearerToken(String authorization) {
        if (authorization == null || authorization.length() <= BEARER.length()
                || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String token = authorization.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static final class TokenRequest extends HttpServletRequestWrapper {
        private final String token;

        private TokenRequest(HttpServletRequest request, String token) {
            super(request);
            this.token = token;
        }

        @Override
        public String getParameter(String name) {
            return "token".equals(name) ? token : super.getParameter(name);
        }

        @Override
        public String[] getParameterValues(String name) {
            return "token".equals(name) ? new String[]{token} : super.getParameterValues(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> values = new LinkedHashMap<>(super.getParameterMap());
            values.put("token", new String[]{token});
            return Collections.unmodifiableMap(values);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(getParameterMap().keySet());
        }
    }
}
