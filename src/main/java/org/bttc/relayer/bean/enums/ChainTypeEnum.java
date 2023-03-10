package org.bttc.relayer.bean.enums;

/**
 * @author tron
 * @date 2021/9/17 7:39
 */
public enum ChainTypeEnum {

  /**
   * Tron
   */
  TRON(1, "Tron", "tron"),

  /**
   * Ethereum
   */
  ETHEREUM(2, "Ethereum", "eth"),

  /**
   * Bsc
   */
  BSC(3, "Bsc", "bsc"),

  /**
   * Btt
   */
  BTT(4, "Btt", "btt");

  public final Integer code;

  public final String desc;

  public final String chainName;

  ChainTypeEnum(int code, String desc, String chainName) {
    this.code = code;
    this.desc = desc;
    this.chainName = chainName;
  }

  public static ChainTypeEnum fromCode(int code) {
    for (ChainTypeEnum chainTypeEnum : ChainTypeEnum.values()) {
      if (chainTypeEnum.code == code) {
        return chainTypeEnum;
      }
    }
    return null;
  }

  public static ChainTypeEnum fromName(String chainName) {
    for (ChainTypeEnum chainTypeEnum : ChainTypeEnum.values()) {
      if (chainTypeEnum.chainName.equals(chainName)) {
        return chainTypeEnum;
      }
    }
    return null;
  }

  public static String getNameByCode(int code) {
    for (ChainTypeEnum chainTypeEnum : ChainTypeEnum.values()) {
      if (chainTypeEnum.code == code) {
        if (chainTypeEnum == BTT) {
          return "bttc";
        }
        return chainTypeEnum.chainName;
      }
    }
    return null;
  }

}
