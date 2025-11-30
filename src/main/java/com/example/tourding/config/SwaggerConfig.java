package com.example.tourding.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${swagger.server.url:http://localhost}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        // serverUrl이 이미 포트를 포함하면 그대로 쓰고, 아니면 포트 붙이기
        String baseUrl = serverUrl.contains(":") ? serverUrl : serverUrl + ":" + serverPort;

        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url(baseUrl).description("현재 서버"),
                        new Server().url("http://localhost:8080").description("로컬 개발 서버")
                ));
    }

    private Info apiInfo() {
        return new Info()
                .title("Tourding API")
                .description("투어딩(Tourding) 애플리케이션을 위한 REST API 문서")
                .version("v1.0.0")
                .contact(new Contact()
                        .name("Tourding")
                        .email("tourding@gmail.com")
                        .url("https://github.com/Giljjang"));
    }

    @Configuration
    @EnableWebSecurity
    public static class SwaggerSecurityConfig {

        @Value("${swagger.user}")
        private String swaggerUser;

        @Value("${swagger.pass}")
        private String swaggerPassword;

        @Bean(name = "swaggerSecurityFilterChain")
        public SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    // Swagger 리소스만 이 체인에서 처리
                    .securityMatcher("/swagger-ui/**", "/swagger-ui.html",
                            "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**")
                    .authorizeHttpRequests(auth -> auth
                            // 문서 JSON/리소스는 공개 (UI가 이걸 불러야 화면이 뜸)
                            .requestMatchers("/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                            // 실제 UI 화면만 Basic 인증 요구
                            .requestMatchers("/swagger-ui/**", "/swagger-ui.html").authenticated()
                            // 혹시 남는 것들 있으면 막기
                            .anyRequest().permitAll()// Swagger는 인증 필요
                    )
                    .httpBasic(Customizer.withDefaults()) // 브라우저 팝업 로그인
                    .csrf(csrf -> csrf.disable())
                    .cors(cors -> cors.disable());
            return http.build();
        }

        @Bean
        public UserDetailsService users() {
            UserDetails user = User.builder()
                    .username(swaggerUser)
                    .password("{noop}" + swaggerPassword) // 임시 비암호화
                    .roles("ADMIN")
                    .build();
            return new InMemoryUserDetailsManager(user);
        }
    }
}
