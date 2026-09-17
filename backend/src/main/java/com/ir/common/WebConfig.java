package com.ir.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.ir.system.audit.OperationLogInterceptor;
import com.ir.system.auth.AuthInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final OperationLogInterceptor operationLogInterceptor;
    @Value("${ir.cors.origins:http://localhost:5174}")
    private String[] origins;

    public WebConfig(
            AuthInterceptor authInterceptor,
            OperationLogInterceptor operationLogInterceptor) {
        this.authInterceptor = authInterceptor;
        this.operationLogInterceptor = operationLogInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Api-Key").maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(operationLogInterceptor).addPathPatterns("/api/**");
    }
}
