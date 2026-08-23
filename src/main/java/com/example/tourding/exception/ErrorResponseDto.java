package com.example.tourding.exception;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponseDto {
    private int code;
    private String message;
}
