package com.example.tourding.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate rt = builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(8))
                .build();
        rt.getInterceptors().add(new LoggingInterceptor());
        return rt;
    }

    @Bean
    public RestTemplate openAiRestTemplate(
            RestTemplateBuilder builder,
            @Value("${ai.request.timeout-ms:15000}") long timeoutMs
    ) {
        RestTemplate rt = builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
        rt.getInterceptors().add(new LoggingInterceptor());
        return rt;
    }
}
