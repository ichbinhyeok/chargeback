package com.example.demo.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class NoIndexFilter extends OncePerRequestFilter {

    private static final String HEADER_VALUE = "noindex, nofollow, noarchive";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/c/") || path.startsWith("/api/") || path.startsWith("/webhooks/")) {
            response.setHeader("X-Robots-Tag", HEADER_VALUE);
        }
        filterChain.doFilter(request, response);
    }
}
