package org.bttc.relayer.bean.common;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.beans.Transient;
import java.util.Optional;

/**
 * With return data, return class.
 * Use @Accessors(chain = true). setXX() returns an object, and it is recommended to use chain calls new DataResponse<>().setData().ok()
 *
 * @author tron
 * @date 2021/9/14 5:01
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "request data", description = "non-paged data request")
public class DataResponse<T> extends Response {
    @ApiModelProperty(value = "response data")
    private T data;

    public DataResponse<T> setData(T data) {
        this.data = data;
        return this;
    }

    public DataResponse<T> optionalData(Optional<T> data) {
        data.ifPresent(t -> this.data = t);
        return this;
    }

    @Transient
    @ApiModelProperty(hidden = true, notes = "hidden and not serialized")
    public boolean isEmpty() {
        return isNotOk() || data == null;
    }

    @Transient
    @ApiModelProperty(hidden = true, notes = "hidden and not serialized")
    public boolean isNotEmpty() {
        return !isEmpty();
    }
}
