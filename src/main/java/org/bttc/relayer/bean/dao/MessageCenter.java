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
 * @date 2021-09-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_center")
@ApiModel("message_center")
public class MessageCenter extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty("id")
    private Long id;

    /**
     * trasactions hash
     */
    private String txId;

    /**
     * the block numer contains the transaction
     */
    private Long blockNumber;

    /**
     * contract Address
     */
    private String contractAddress;

    /**
     * from address of the transaction
     */
    private String fromAddress;

    /**
     * to address of the transaction
     */
    private String toAddress;

    /**
     * from chain of the transaction
     */
    private String fromChainId;

    /**
     * to chain of the transaction
     */
    private String toChainId;

    /**
     * the token address in the transaction
     */
    private String tokenId;

    /**
     * the token amount in the transaction
     */
    private String amount;

    /**
     *  fee of the transaction
     */
    private String fee;

    /**
     * the fee of transferring refuel
     */
    private String tsfFee;

    /**
     * event type
     */
    private String eventType;

    /**
     * the status of the transaction: 0-not finished 1-finished 2-fail
     */
    private int status;

    /**
     * the index of the event log
     */
    private int eventIndex;

    /**
     * the content of the event
     */
    private String content;

    /**
     * the trasaction time
     */
    private String timeStamp;
}
