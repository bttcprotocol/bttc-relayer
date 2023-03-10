package org.bttc.relayer.bean.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import java.io.Serializable;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @Author: tron
 * @Date: 2022/1/17 4:36 PM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ApiModel("transaction record")
public class Transactions extends BaseEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  /**
   * the transaction hash of the src chain
   */
  @TableField("src_txid")
  private String srcTxid;

  /**
   * the transaction hash of the dest chain
   */
  @TableField("dest_txid")
  private String destTxid;

  /**
   * the from address on the src chain
   */
  @TableField("from_address")
  private String fromAddress;

  /**
   * the receive address on the dest chain
   */
  @TableField("to_address")
  private String toAddress;

  /**
   * the account send the transaction on the dest chain
   */
  @TableField("dest_tx_owner")
  private String destTxOwner;

  /**
   * the chain id of the src transaction: 1-tron;2-eth;3-bsc;4-btt
   */
  @TableField("src_chain_id")
  private Integer srcChainId;

  /**
   * the chain id of the dest transaction: 1-tron;2-eth;3-bsc;4-btt
   */
  @TableField("dest_chain_id")
  private Integer destChainId;

  /**
   * the token operated in the src chain
   */
  @TableField("src_token_id")
  private String srcTokenId;

  /**
   * the token received in the dest chain
   */
  @TableField("dest_token_id")
  private String destTokenId;

  /**
   * the token amount operated on the src chain
   */
  @TableField("from_amount")
  private String fromAmount;

  /**
   * the token amount received in the dest chain
   */
  @TableField("to_amount")
  private String toAmount;

  /**
   * the nonce of the dest transaction
   */
  @TableField("nonce")
  private String nonce;

  /**
   * the transaction fee
   */
  @TableField("fee")
  private String fee;

  /**
   * the block number contains the src transaction
   */
  @TableField("from_block")
  private Long fromBlock;

  /**
   * the block number contains the dest transaction
   */
  @TableField("to_block")
  private Long toBlock;

  /**
   * the status of the transaction
   */
  @TableField("t_status")
  private Integer tStatus;

  /**
   * the result of the src transaction
   */
  @TableField("src_contract_ret")
  private String srcContractRet;

  /**
   * the status of the dest transaction
   */
  @TableField("dest_contract_ret")
  private String destContractRet;

  /**
   * the time of the src transaction
   */
  @TableField("src_timestamp")
  private Date srcTimestamp;

  /**
   * the time of the dest transaction
   */
  @TableField("dest_timestamp")
  private Date destTimestamp;

  /**
   * the related content of the transaction
   */
  @TableField("content")
  private String content;

  /**
   * the update time of the transaction
   */
  @TableField("update_time")
  private Date updateTime;


  /**
   * Rewrite the setter method of some properties, and convert src and dest hash, to lowercase
   */
  public void setSrcTxid(String srcTxid) {
    this.srcTxid = srcTxid.toLowerCase();
  }

  public void setDestTxid(String destTxid) {
    this.destTxid = destTxid.toLowerCase();
  }

}
