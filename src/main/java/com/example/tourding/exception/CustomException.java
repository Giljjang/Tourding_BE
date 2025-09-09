package com.example.tourding.exception;

import com.example.tourding.enums.ErrorCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final ErrorCode code;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

}
