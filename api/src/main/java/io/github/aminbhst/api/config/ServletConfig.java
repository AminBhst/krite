package io.github.aminbhst.api.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;

@Configuration
public class ServletConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement(MultipartProperties properties) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(properties.getMaxFileSize());
        factory.setMaxRequestSize(properties.getMaxRequestSize());
        return factory.createMultipartConfig();
    }
}
