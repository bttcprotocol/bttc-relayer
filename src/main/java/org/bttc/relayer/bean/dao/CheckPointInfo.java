package org.bttc.relayer.bean.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_point_info")
@ApiModel("check_point_info")
public class CheckPointInfo extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty("id")
    private Long id;

    /**
     * the number of check point
     */
    private Long checkPointNum;

    /**
     * transaction hash
     */
    private String txId;

    /**
     * the start block number packaged in the checkpoint
     */
    private Long startBlock;

    /**
     * the end block number packaged in the checkpoint
     */
    private Long endBlock;

    /**
     * the block number packaged the checkpoint transaction
     */
    private Long blockNumber;

    /**
     * the chain id of the checkpoint:1-tron; 2-eth; 3-bsc
     */
    private Integer chainId;

    /**
     * the result of the checkpoint transaction, whether success
     */
    private String result;

    /**
     * whether the checkpoint transaction confirmed
     */
    private Integer confirm;

    /**
     * the time of the checkpoint transaction
     */
    private Date timeStamp;
}
