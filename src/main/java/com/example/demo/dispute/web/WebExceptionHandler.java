package com.example.demo.dispute.web;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(assignableTypes = WebCaseController.class)
public class WebExceptionHandler {

    private static final String CASE_ACCESS_ERROR_MESSAGE =
            "Case not found or expired. Start a new case or reopen with a valid access link.";

    @ExceptionHandler(EntityNotFoundException.class)
    public void handleCaseNotFound(HttpServletResponse response) throws IOException {
        response.sendRedirect("/?error=" + URLEncoder.encode(CASE_ACCESS_ERROR_MESSAGE, StandardCharsets.UTF_8));
    }
}
