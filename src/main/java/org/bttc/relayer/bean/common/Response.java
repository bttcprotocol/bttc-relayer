package org.bttc.relayer.bean.common;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.beans.Transient;

/**
 * @author tron
 * @date 2021/9/14 4:59
 */
@Data
@Accessors(chain = true)
@ApiModel(value = "general response")
public class Response {
    /**
     * return status code
     */
    @ApiModelProperty(value = "status code，0：normal")
    protected Integer code;
    @ApiModelProperty(value = "description")
    protected String msg;


    /**
     * setter
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T setCode(Integer code) {
        this.code = code;
        return (T) this;
    }

    /**
     * setter
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T setMsg(String msg) {
        this.msg = msg;
        return (T) this;
    }

    /**
     * Quick dichotomy marker, whether it succeeds or fails.
     *
     * @param status whether success
     * @param <T> Response subclass
     * @return result object
     */
    public <T extends Response> T markStatus(boolean status) {
        return markCode(status ? StatusCode.OK : StatusCode.FAIL);
    }

    /**
     * Mark status by StatusCode
     *
     * @param code status code
     * @param <T> Response subclass
     * @return result object
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T markCode(StatusCode code) {
        this.code = code.getCode();
        this.msg = code.getMsg();
        return (T) this;
    }

    /**
     * mark success
     *
     * @param <T> Response subclass
     * @return result object
     */
    public <T extends Response> T ok() {
        return markCode(StatusCode.OK);
    }

    /**
     * mark fail
     *
     * @param <T> Response subclass
     * @return result object
     */
    public <T extends Response> T fail() {
        return markCode(StatusCode.FAIL);
    }

    /**
     * mark failed
     *
     * @param msg failure message
     * @param <T> Response subclass
     * @return result object
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T fail(String msg) {
        this.code = StatusCode.FAIL.getCode();
        this.msg = msg;
        return (T) this;
    }

    /**
     * whether succeed
     * not serialized
     *
     * @return success
     */
    @Transient
    @ApiModelProperty(hidden = true, notes = "hidden and not serialized")
    public boolean isOk() {
        return StatusCode.OK.getCode().equals(code);
    }

    /**
     * whether unsuccessful
     * not serialized
     *
     * @return unsuccessful
     */
    @Transient
    @ApiModelProperty(hidden = true, notes = "hidden and not serialized")
    public boolean isNotOk() {
        return !isOk();
    }
}
