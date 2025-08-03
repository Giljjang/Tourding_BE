package com.example.tourding.exception;

import com.example.tourding.enums.RouteApiCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final RouteApiCode code;

    public CustomException(RouteApiCode code) {
        super(code.getMessage());
        this.code = code;
    }
}
