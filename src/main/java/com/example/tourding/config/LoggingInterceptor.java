package com.example.tourding.config;

import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;

public class LoggingInterceptor implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(
            org.springframework.http.HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws java.io.IOException {

        System.out.println("=== Outgoing Request ===");
        System.out.println("URI: " + request.getURI());
        System.out.println("Method: " + request.getMethod());
        System.out.println("Headers: " + sanitizeHeaders(request.getHeaders()));
        if (body.length > 0) {
            System.out.println("Body: " + new String(body, StandardCharsets.UTF_8));
        }

        ClientHttpResponse response = execution.execute(request, body);

        System.out.println("=== Incoming Response ===");
        System.out.println("Status code: " + response.getStatusCode());
        System.out.println("Response Headers: " + sanitizeHeaders(response.getHeaders()));
        // body는 스트림이기 때문에 복제해서 읽어야 함 (지금은 생략하거나 필요하면 래핑)

        return response;
    }

    private org.springframework.http.HttpHeaders sanitizeHeaders(org.springframework.http.HttpHeaders headers) {
        org.springframework.http.HttpHeaders sanitized = new org.springframework.http.HttpHeaders();
        sanitized.putAll(headers);
        mask(sanitized, "Authorization");
        mask(sanitized, "Cookie");
        mask(sanitized, "Set-Cookie");
        return sanitized;
    }

    private void mask(org.springframework.http.HttpHeaders headers, String name) {
        if (!CollectionUtils.isEmpty(headers.get(name))) {
            headers.set(name, "[REDACTED]");
        }
    }
}
