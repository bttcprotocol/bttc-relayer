package org.bttc.relayer.bean.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_center_config")
@ApiModel("message_center_config")
@Builder
public class MessageCenterConfig extends BaseEntity implements Serializable {

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

  @TableField(value = "contract_address")
  private String contractAddress;

  /**
   * the max confirmed block
   */
  private long maxConfirmBlock;

  /**
   * the max unconfirmed block
   */
  private long maxUnconfirmBlock;
}
