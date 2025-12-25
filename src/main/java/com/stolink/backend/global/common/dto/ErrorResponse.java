package com.stolink.backend.global.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private boolean success = false;
    private ErrorDetail error;

    public ErrorResponse(String code, String message) {
        this.error = new ErrorDetail(code, message);
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorDetail {
        private String code;
        private String message;
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message);
    }
}
