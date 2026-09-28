package com.example.tourding.external.apple.service;

import com.example.tourding.enums.ErrorCode;
import com.example.tourding.exception.CustomException;
import com.example.tourding.external.apple.dto.AppleAuthTokenResponseDto;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleAuthService {
    @Value("${apple.bundle_id}")
    private String BUNDLEID;

    @Value("${apple.iss}")
    private String ISS;

    @Value("${apple.private_key}")
    private String PRIVATE_KEY;

    @Value("${apple.key_id}")
    private String KID;

    public PrivateKey loadPrivateKey() throws Exception {
        if (PRIVATE_KEY == null || PRIVATE_KEY.isBlank()) {
            throw new IllegalStateException("APPLE_PRIVATE_KEY가 비어 있습니다.");
        }

        // Docker 환경변수에서는 PEM의 개행이 문자 그대로 "\\n"으로 들어올 수 있고,
        // .env 작성 과정에서 PEM 헤더의 대시가 유니코드 문자로 바뀔 수 있다.
        String configuredKey = PRIVATE_KEY.trim();
        if (configuredKey.startsWith("\"") && configuredKey.endsWith("\"")) {
            configuredKey = configuredKey.substring(1, configuredKey.length() - 1);
        }
        configuredKey = configuredKey
                .replace("\\r", "\n")
                .replace("\\n", "\n")
                .replace('\r', '\n')
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2212', '-');

        int beginMarker = configuredKey.indexOf("BEGIN PRIVATE KEY");
        int endMarker = configuredKey.indexOf("END PRIVATE KEY");
        String privateKey = beginMarker >= 0 && endMarker > beginMarker
                ? configuredKey.substring(beginMarker + "BEGIN PRIVATE KEY".length(), endMarker)
                : configuredKey;
        privateKey = privateKey.replaceAll("\\s+", "")
                // BEGIN/END marker 뒤에 남은 PEM 구분문자 제거
                .replaceFirst("^[^A-Za-z0-9+/=]+", "")
                .replaceFirst("[^A-Za-z0-9+/=]+$", "");

        if (!privateKey.matches("[A-Za-z0-9+/]+={0,2}")) {
            throw new IllegalArgumentException("APPLE_PRIVATE_KEY의 PEM 본문에 허용되지 않은 문자가 있습니다.");
        }

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

        try {
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", authorizationCode);
            params.add("client_id", BUNDLEID);
            params.add("client_secret", createClientSecret());
            params.add("grant_type", "authorization_code");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(params, headers);

            ResponseEntity<AppleAuthTokenResponseDto> response = restTemplate.postForEntity(authUrl, httpEntity, AppleAuthTokenResponseDto.class);
            if(!response.getStatusCode().is2xxSuccessful()) {
                throw new CustomException(ErrorCode.APPLE_WITHDRAW_FAILED);
            }
            log.info("Apple Auth Token 요청 성공 : status={}", response.getStatusCode());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Apple Auth Token 요청 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.APPLE_WITHDRAW_FAILED);
        } catch (Exception e) {
            log.error("Apple client_secret 생성 또는 Auth Token 요청 준비 실패", e);
            throw new CustomException(ErrorCode.APPLE_WITHDRAW_FAILED);
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
                .signWith(privateKey, SignatureAlgorithm.ES256)
                .compact();
    }

    public void revoke(String authorizationCode) throws Exception {
        AppleAuthTokenResponseDto appleAuthToken = GenerateAuthToken(authorizationCode);
        if (appleAuthToken == null || appleAuthToken.getRefresh_token() == null) {
            throw new CustomException(ErrorCode.APPLE_WITHDRAW_FAILED);
        }

        try {
            RestTemplate restTemplate = new RestTemplateBuilder().build();
            String revokeUrl = "https://appleid.apple.com/auth/revoke";
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", BUNDLEID);
            params.add("client_secret", createClientSecret());
            params.add("token", appleAuthToken.getRefresh_token());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(params, headers);
            restTemplate.postForEntity(revokeUrl, httpEntity, String.class);
            log.info("Apple revoke 요청 성공");
        } catch (HttpClientErrorException e) {
            log.error("Apple revoke 요청 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.APPLE_WITHDRAW_FAILED);
        } catch (Exception e) {
            log.error("Apple revoke 처리 실패", e);
            throw new CustomException(ErrorCode.APPLE_WITHDRAW_FAILED);
        }
    }
}
