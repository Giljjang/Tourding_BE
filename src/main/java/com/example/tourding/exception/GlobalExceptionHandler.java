package com.example.tourding.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponseDto> handleCustomException(CustomException e) {
        return ResponseEntity.status(e.getCode().getStatus())
                .body(ErrorResponseDto.builder()
                        .code(e.getCode().getCode())
                        .message(e.getCode().getMessage())
                        .build());
    }
}
