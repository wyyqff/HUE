package com.unimarket.common.config;

import com.unimarket.security.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorsConfigurationConsistencyTest {

    @Test
    void productionOriginIsAcceptedBySharedServletAndSecurityPolicy() throws Exception {
        CorsConfigurationSource source = sourceFor("https://campus.example.edu, https://admin.example.edu");
        MockHttpServletRequest request = preflight("https://campus.example.edu");
        assertEquals("https://campus.example.edu", source.getCorsConfiguration(request).checkOrigin("https://campus.example.edu"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        new CorsConfig().corsFilter(source).doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals("https://campus.example.edu", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void unexpectedOriginIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CorsConfig().corsFilter(sourceFor("https://campus.example.edu"))
                .doFilter(preflight("https://other.example.edu"), response, new MockFilterChain());
        assertEquals(403, response.getStatus());
    }

    @Test
    void localDefaultsRemainAvailableWithoutConfiguration() throws Exception {
        String expression = SecurityConfig.class.getDeclaredField("allowedOriginPatterns")
                .getAnnotation(Value.class).value();
        String defaultOrigins = expression.substring(expression.indexOf(':') + 1, expression.length() - 1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CorsConfig().corsFilter(sourceFor(defaultOrigins))
                .doFilter(preflight("http://localhost:5173"), response, new MockFilterChain());
        assertEquals(200, response.getStatus());
        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
    }

    private CorsConfigurationSource sourceFor(String origins) {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "allowedOriginPatterns", origins);
        return config.corsConfigurationSource();
    }

    private MockHttpServletRequest preflight(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/goods");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Content-Type, X-CSRF-TOKEN");
        return request;
    }
}
