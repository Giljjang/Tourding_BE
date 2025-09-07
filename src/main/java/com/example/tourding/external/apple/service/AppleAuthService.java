package com.example.tourding.external.apple.service;

import com.example.tourding.enums.RouteApiCode;
import com.example.tourding.exception.CustomException;
import com.example.tourding.external.apple.dto.AppleAuthTokenResponseDto;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.springframework.security.oauth2.jwt.JoseHeaderNames.KID;

@Service
@RequiredArgsConstructor
@Component

public class AppleAuthService {
    @Value("${apple.bundleid}")
    private String BUNDLEID;

    @Value("${apple.iss}")
    private String ISS;

    @Value("${apple.private_key}")
    private String PRIVATE_KEY;

    public PrivateKey loadPrivateKey() throws Exception {
        String privateKey = PRIVATE_KEY
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        // Base64 디코딩
        byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(privateKey);

        // PrivateKey 생성
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePrivate(keySpec);
    }

    public AppleAuthTokenResponseDto GenerateAuthToken(String authorizationCode) throws Exception {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        String authUrl = "https://appleid.apple.com/auth/token";

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", authorizationCode);
        params.add("client_id", BUNDLEID);
        params.add("client_secret", createClientSecret());
        params.add("grant_type", "authorization_code");
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(params, headers);
        try {
            ResponseEntity<AppleAuthTokenResponseDto> response = restTemplate.postForEntity(authUrl, httpEntity, AppleAuthTokenResponseDto.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            System.out.println(e);
            throw new CustomException(RouteApiCode.APPLE_WITHDRAW_FAILED);
        }
    }

    public String createClientSecret() throws Exception {
        PrivateKey privateKey = loadPrivateKey();
        Date expirationDate = Date.from(LocalDateTime.now().plusDays(30).atZone(ZoneId.systemDefault()).toInstant());
        Map<String, Object> jwtHeader = new HashMap<>();
        jwtHeader.put("kid", KID); // kid
        jwtHeader.put("alg", "ES256"); // alg
        return Jwts.builder()
                .setHeaderParams(jwtHeader)
                .setIssuer(ISS) // iss
                .setIssuedAt(new Date(System.currentTimeMillis())) // 발행 시간
                .setExpiration(expirationDate) // 만료 시간
                .setAudience("https://appleid.apple.com") // aud
                .setSubject(BUNDLEID) // sub
                .signWith(privateKey)
                .compact();
    }

    public void revoke(String authorizationCode) throws Exception {
        AppleAuthTokenResponseDto appleAuthToken = GenerateAuthToken(authorizationCode);
        if (appleAuthToken.getAccessToken() != null) {
            RestTemplate restTemplate = new RestTemplateBuilder().build();
            String revokeUrl = "https://appleid.apple.com/auth/revoke";
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", BUNDLEID);
            params.add("client_secret", createClientSecret());
            params.add("token", appleAuthToken.getAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(params, headers);
            restTemplate.postForEntity(revokeUrl, httpEntity, String.class);
        }
    }
}
