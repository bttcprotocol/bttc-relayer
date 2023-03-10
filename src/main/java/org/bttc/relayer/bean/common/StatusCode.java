package org.bttc.relayer.bean.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * request response status code
 * Only base status codes are defined. Please define additionally if it is related to the business,and keep the error code within 0~99
 *
 * @author tron
 * @date 2021/9/14 5:00 pm
 */
@Getter
@AllArgsConstructor
public enum StatusCode {
    /**
     * request success
     */
    OK(0, "request success"),
    /**
     * request fail
     */
    FAIL(1, "request fail"),
    /**
     * request breaker
     */
    BREAKER(2, "request breaker"),
    /**
     *
     */
    NOT_EXIST(3, "data is not exist"),
    ;
    private final Integer code;
    private final String msg;

    public static StatusCode fromCode(Integer value) {
        return Arrays.stream(StatusCode.values()).filter(it -> it.getCode().equals(value)).findFirst().orElse(null);
    }

    /**
     * Get the name by code, return the default value if get nothing
     *
     * @param code code
     * @param defaultName default name
     * @return response
     */
    public static String getNameWithDefault(Integer code, String defaultName) {
        StatusCode statusCode = fromCode(code);
        return statusCode == null ? defaultName : statusCode.getMsg();
    }
}
