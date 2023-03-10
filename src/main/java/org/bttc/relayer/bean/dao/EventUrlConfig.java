package org.bttc.relayer.bean.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author tron
 * @date 2021-09-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("event_url_config")
@ApiModel("event_url_config")
public class EventUrlConfig extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty("id")
    private Long id;

    /**
     * chain
     */
    @ApiModelProperty("chain")
    private String chain;

    /**
    * url
    */
    @ApiModelProperty("url")
    private String url;
}
