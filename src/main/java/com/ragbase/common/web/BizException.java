package com.ragbase.common.web;

import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this("500", message);
    }
}
