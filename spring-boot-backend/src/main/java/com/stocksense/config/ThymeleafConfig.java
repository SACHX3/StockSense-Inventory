package com.stocksense.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ThymeleafConfig implements WebMvcConfigurer {
    // Spring Boot auto-configures Thymeleaf with #request available
    // This class ensures the request context is properly set up
    // #request in Thymeleaf refers to HttpServletRequest directly
}
