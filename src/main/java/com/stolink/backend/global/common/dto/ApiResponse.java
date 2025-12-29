package com.stolink.backend.global.common.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiResponse<T> {

    private final int code;
    private final HttpStatus status;
    private final String message;
    private final T data;

    @Builder
    private ApiResponse(HttpStatus status, String message, T data) {
        this.code = status.value();
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(HttpStatus.OK, "OK", data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(HttpStatus.CREATED, "Created", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(HttpStatus.OK, "OK", null);
    }

    public static <T> ApiResponse<T> accepted(T data) {
        return new ApiResponse<>(HttpStatus.ACCEPTED, "Accepted", data);
    }
}
