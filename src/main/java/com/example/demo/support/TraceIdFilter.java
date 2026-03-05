package com.example.demo.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private final String headerName;

    public TraceIdFilter(@Value("${app.trace.header-name:X-Trace-Id}") String headerName) {
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = request.getHeader(headerName);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        } else {
            traceId = sanitize(traceId);
        }

        MDC.put("traceId", traceId);
        response.setHeader(headerName, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9\\-_.]", "");
    }
}
