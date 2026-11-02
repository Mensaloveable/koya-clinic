package com.clinic.koya.koya_clinic_api.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class ApiMappingSecurity implements WebMvcConfigurer {

    public static final String API_PACKAGE = "com.clinic.koya.koya_clinic_api";

    private final RequestSecurity requestSecurity;

    public ApiMappingSecurity(RequestSecurity requestSecurity) {
        this.requestSecurity = requestSecurity;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    @NonNull HttpServletRequest request,
                    @NonNull HttpServletResponse response,
                    @NonNull Object handler
            ) throws Exception {

                if (
                        handler instanceof HandlerMethod method
                        && method.getBeanType().getPackageName().startsWith(API_PACKAGE)
                        && !requestSecurity.authorized(request)
                ) {
                    RequestSecurity.unauthorized(response);
                    return false;
                }
                return true;
            }
        });
    }
}
