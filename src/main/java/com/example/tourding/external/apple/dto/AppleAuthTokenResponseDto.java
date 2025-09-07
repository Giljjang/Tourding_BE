package com.example.tourding.external.apple.dto;

import lombok.Getter;

@Getter

public class AppleAuthTokenResponseDto {
        String accessToken;
        Integer expires_in;
        String id_token;
        String refresh_token;
        String token_type;
}
