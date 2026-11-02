package com.clinic.koya.koya_clinic_api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

public class FallbackErrors implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(FallbackErrors.class);

    @RequestMapping("/error")
    public ResponseEntity<Map<String, String>> error(HttpServletRequest request) {

        Object code = request.getAttribute("javax.servlet.error.status_code");

        int statusCode = code != null ? Integer.parseInt(code.toString()) : HttpStatus.INTERNAL_SERVER_ERROR.value();
        Object message = statusCode == HttpStatus.NOT_FOUND.value() ? request.getAttribute("javax.servlet.error.message") : "request failed";

        log.debug("Fallback error handler invoked:\nstatus=>{}\nmessage=>{}\npath=>{}",
                statusCode, message, request.getAttribute("jakarta.servlet.error.request_uri"));

        return ResponseEntity.status(statusCode).body(Map.of("error", message.toString()));
    }
}
