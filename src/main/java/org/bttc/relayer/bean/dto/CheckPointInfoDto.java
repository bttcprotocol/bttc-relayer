package org.bttc.relayer.bean.dto;


import io.swagger.annotations.ApiModel;
import lombok.Data;

@Data
@ApiModel
public class CheckPointInfoDto {

  /**
   * The chain id of the check point: 1-tron,2-eth;3-bsc
   */
  private Integer chainId;

  /**
   * The check point number
   */
  private Long checkPointNum;

  /**
   * the start block number packaged in the checkpoint
   */
  private Long start;

  /**
   * the end block number packaged in the checkpoint
   */
  private Long end;
}
