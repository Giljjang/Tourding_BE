package com.example.tourding.exception;

import com.example.tourding.enums.ErrorCode;
import jakarta.persistence.EntityNotFoundException;
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

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleEntityNotFoundException(EntityNotFoundException e) {
        return ResponseEntity.status(ErrorCode.USER_NOT_FOUND.getStatus())
                .body(ErrorResponseDto.builder()
                        .code(ErrorCode.USER_NOT_FOUND.getCode())
                        .message(resolveMessage(e, ErrorCode.USER_NOT_FOUND.getMessage()))
                        .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponseDto.builder()
                        .code(ErrorCode.INVALID_REQUEST.getCode())
                        .message(resolveMessage(e, ErrorCode.INVALID_REQUEST.getMessage()))
                        .build());
    }

    private String resolveMessage(Exception e, String defaultMessage) {
        return e.getMessage() == null || e.getMessage().isBlank() ? defaultMessage : e.getMessage();
    }
}
