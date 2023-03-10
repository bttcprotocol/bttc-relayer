package org.bttc.relayer.bean.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("token map table")
public class TokenMap extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty("id")
    private Long id;

    /**
     * tokenName
     */
    @ApiModelProperty("tokenName")
    private String tokenName;

    /**
     * token desc
     */
    @ApiModelProperty("desc")
    private String tokenDesc;

    /**
     * chainId
     */
    @ApiModelProperty("chainId")
    private Integer chainId;

    /**
     * mainAddress
     */
    @ApiModelProperty("mainAddress")
    private String mainAddress;

    /**
     * childAddress
     */
    @ApiModelProperty("childAddress")
    private String childAddress;

    /**
     * status
     */
    @ApiModelProperty("status")
    private Integer status;

    /**
     * tokenPrecision
     */
    private Integer tokenPrecision;

    /**
     * main token symbol
     */
    private String symbolMain;

    /**
     * main token name
     */
    private String nameMain;

    /**
     * swapFlag
     */
    private Integer swapFlag;

    /**
     * create time
     */
    private Date createTime;

    /**
     * update time
     */
    private Date updateTime;

    public TokenMap() {}

    public TokenMap(Integer chainId) {
        this.chainId = chainId;
    }

    public TokenMap(String tokenName, Integer tokenPrecision) {
        this.tokenName = tokenName;
        this.tokenPrecision = tokenPrecision;
    }
}
