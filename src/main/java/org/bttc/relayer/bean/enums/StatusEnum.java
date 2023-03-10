package org.bttc.relayer.bean.enums;

/**
 * Transaction status enumeration class
 *
 * @author tron
 */
public enum StatusEnum {
  // normal status
  /**
   * the src transaction is success
   */
  SRC_CHAIN_HANDLED(1, "src_chain_handled"),
  /**
   * the transaction has been packaged in the checkpoint and can receive the asset on the dest chain
   */
  SWAP_CHAIN_HANDLED(2, "swap_chain_handled"),
  /**
   * the src transaction has been launched
   */
  DEST_CHAIN_LAUNCHED(3, "dest_chain_launched"),
  /**
   * the dest transaction is success but not confirmed
   */
  DEST_CHAIN_ON_CHAIN(4, "dest_chain_on_chain"),

  /**
   * the dest transaction is success and confirmed. now the whole withdraw successed
   */
  DEST_CHAIN_HANDLED(200, "dest_chain_handled"),

  // processing status
  /**
   * the dest transaction is pending
   */
  DEST_CHAIN_HANDLING(130, "dest_chain_handling"),

  // error status
  /**
   * dest hash fail
   */
  DEST_CHAIN_HANDLE_FAILED(252, "dest_chain_handle_failed"),
  /**
   * dest hash timeout, it is not packaged in the block in constant time
   */
  DEST_CHAIN_HANDLE_TIMEOUT(251, "dest_chain_handle_timeout"),
  /**
   * the dest hash error
   */
  DEST_CHAIN_HASH_ERROR(248, "dest_chain_hash_error");


  private final int value;
  private final String desc;

  StatusEnum(int value, String desc) {
    this.value = value;
    this.desc = desc;
  }

  public int getValue() {
    return value;
  }

  public String getDesc() {
    return desc;
  }

  public static StatusEnum getStatusEnum(Integer value) {
    switch (value) {
      case 1:
        return StatusEnum.SRC_CHAIN_HANDLED;
      case 2:
        return StatusEnum.SWAP_CHAIN_HANDLED;
      case 3:
        return StatusEnum.DEST_CHAIN_LAUNCHED;
      case 4:
        return StatusEnum.DEST_CHAIN_ON_CHAIN;
      case 200:
        return StatusEnum.DEST_CHAIN_HANDLED;
      case 130:
        return StatusEnum.DEST_CHAIN_HANDLING;
      case 252:
        return StatusEnum.DEST_CHAIN_HANDLE_FAILED;
      case 251:
        return StatusEnum.DEST_CHAIN_HANDLE_TIMEOUT;
      case 248:
        return StatusEnum.DEST_CHAIN_HASH_ERROR;
      default:
        return null;
    }
  }
}
