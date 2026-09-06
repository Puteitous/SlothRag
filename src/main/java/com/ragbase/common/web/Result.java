package com.ragbase.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    public static final String SUCCESS_CODE = "0";

    private String code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "success", data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> failure(String code, String message) {
        return new Result<>(code, message, null);
    }
}
