package org.bttc.relayer.schedule;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import org.bttc.relayer.utils.SignUtils;
import org.spongycastle.util.encoders.Hex;

/**
 * @author tron
 */
@SuppressWarnings("squid:S125")
public class Util {

  private Util() {
  }

  public static final int RETRY_TIMES = 5;
  public static final int BLOCK_RANGE = 500;

  public static final String EXIT_ALREADY_PROCESSED = "EXIT_ALREADY_PROCESSED";

  public static final String SUBMIT_WITHDRAW = "SubmitWithdraw";
  public static final String TRANSFER = "Transfer";
  public static final String LOCKED_ETHER = "LockedEther"; //chain token
  public static final String EXITED_ETHER = "ExitedEther";
  public static final String EXITED_ERC20 = "ExitedERC20";
  public static final String EXITED_MINTABLE_ERC20 = "ExitedMintableERC20";
  public static final String WITHDRAW_TO = "WithdrawTo";
  public static final String EXIT_TOKEN_TO = "ExitTokenTo";
  public static final String DEPOSIT = "Deposit";
  public static final String LOCKED_ERC20 = "LockedERC20";
  public static final String LOCKED_MINTABLE_ERC20 = "LockedMintableERC20";
  public static final String NEW_HEADER_BLOCK = "NewHeaderBlock";
  public static final String RELAY_EXIT = "RelayExit";

  public static final String TRANSFER_TOPICS =
      "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
  // LockedEther(index_topic_1 address from, index_topic_2 address to, uint256 amount)
  public static final String LOCKED_ETHER_TOPICS =
      "0x3e799b2d61372379e767ef8f04d65089179b7a6f63f9be3065806456c7309f1b";
  public static final String WITHDRAW_TO_TOPICS =
      "0x67b714876402c93362735688659e2283b4a37fb21bab24bc759ca759ae851fd8";
  // ExitTokenTo(address indexed from, address indexed to, address indexed fromToken,
  // address indexed toToken, uint256 amount);
  public static final String EXIT_TOKEN_TO_TOPICS =
      "0x29aad86b46fcfbc8552c55f312a1420a2f563f94aa18e923f292f04f8d0b320a";
  public static final String DEPOSIT_TOPICS =
      "0x4e2ca0515ed1aef1395f66b5303bb5d6f1bf9d61a353fa53f73f8ac9973fa9f6";
  // LockedERC20(index_topic_1 address from, index_topic_2 address to,
  // index_topic_3 address rootToken, uint256 amount)
  public static final String LOCKED_ERC20_TOPICS =
      "0x9b217a401a5ddf7c4d474074aff9958a18d48690d77cc2151c4706aa7348b401";
  // LockedMintableERC20(index_topic_1 address from, index_topic_2 address to,
  // index_topic_3 address rootToken, uint256 amount)
  public static final String LOCKED_MINTABLE_ERC20_TOPICS =
      "0x31472eae9e158460fea5622d1fcb0c5bdc65b6ffb51827f7bc9ef5788410c34c";
  // ExitedEther(address,uint256)
  public static final String EXITED_ETHER_TOPICS =
      "0x0fc0eed41f72d3da77d0f53b9594fc7073acd15ee9d7c536819a70a67c57ef3c";
  // NewHeaderBlock (index_topic_1 address proposer, index_topic_2 uint256 headerBlockId,
  // index_topic_3 uint256 reward, uint256 start, uint256 end, bytes32 root)
  // ExitedERC20(address indexed withdrawer, address indexed rootToken, uint256 amount)
  public static final String EXITED_ERC20_TOPICS =
      "0xbb61bd1b26b3684c7c028ff1a8f6dabcac2fac8ac57b66fa6b1efb6edeab03c4";
  // ExitedMintableERC20(address indexed withdrawer, address indexed rootToken, uint256 amount)
  public static final String EXITED_MINTABLE_ERC20_TOPICS =
      "0x42315cb7471194a6f162099cd1052b95b750612a46472e887f7784b95aa2c4c3";

  public static final String NEW_HEADER_BLOCK_TOPICS =
      "0xba5de06d22af2685c6c7765f60067f7d2b08c2d29f53cdf14d67f6d1c9bfb527";

  @SuppressWarnings("squid:S125")
  // event RelayExit(uint256 indexed id, address indexed relayer, address to,
  //        address tokenWithdraw, address tokenExit, uint256 actual,
  //        uint256 fee, bool withRefuel, uint256 refuelFee);
  public static final String RELAY_EXIT_REFUEL_TOPICS =
      "0x7383e64e7eb9ec3b3552d566452396b772344d194636e943d9ba537cee6d7eca";

  // depositTokenFor(address user, address rootToken, bytes depositData)
  public static final String FUNC_SIG_DEPOSIT_TOKEN_FOR = "0x" + Hex.toHexString(
      SignUtils.sha3("depositTokenFor(address,address,bytes)".getBytes())).substring(0, 8);
  public static final String FUNC_SIG_DEPOSIT_FOR = "0x" + Hex.toHexString(
      SignUtils.sha3("depositFor(address)".getBytes())).substring(0, 8);
  //depositPoolTokenFor(address user,address poolToken,bytes depositData)
  public static final String FUNC_SIG_DEPOSIT_POOL_TOKEN_FOR = "0x" + Hex.toHexString(
      SignUtils.sha3("depositPoolTokenFor(address,address,bytes)".getBytes())).substring(0, 8);

  //getLastChildBlock
  public static final String FUNC_GET_LAST_CHILD_BLOCK = "0x" + Hex.toHexString(
      SignUtils.sha3("getLastChildBlock()".getBytes())).substring(0, 8);

  //currentHeaderBlock
  public static final String FUNC_CURRENT_HEADER_BLOCK = "0x" + Hex.toHexString(
      SignUtils.sha3("currentHeaderBlock()".getBytes())).substring(0, 8);

  //headerBlocks
  public static final String FUNC_HEADER_BLOCK = "0x" + Hex.toHexString(
      SignUtils.sha3("headerBlocks(uint256)".getBytes())).substring(0, 8);

  public static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
  public static final String BTT_ADDRESS = "0x0000000000000000000000000000000000001010";

  public static final String DATE_FORMAT_MATCHER = "yyyy-MM-dd HH:mm:ss";
  public static final String GMT8_MATCHER = "GMT+8";

  public static final long ONE_MINUTE_IN_MILLS = 60 * 1000L;

  /**
   * the map of event topic and event name of eth/bsc
   */
  private static final Map<String, String> NEED_PARSE_TOPICS;

  public static Map<String, String> getNeedParseTopics() {
    return NEED_PARSE_TOPICS;
  }

  static {
    NEED_PARSE_TOPICS = new HashMap<>(12);
    NEED_PARSE_TOPICS.put(LOCKED_ETHER_TOPICS, LOCKED_ETHER);
    NEED_PARSE_TOPICS.put(LOCKED_ERC20_TOPICS, LOCKED_ERC20);
    NEED_PARSE_TOPICS.put(LOCKED_MINTABLE_ERC20_TOPICS, LOCKED_MINTABLE_ERC20);
    NEED_PARSE_TOPICS.put(DEPOSIT_TOPICS, DEPOSIT);
    NEED_PARSE_TOPICS.put(WITHDRAW_TO_TOPICS, WITHDRAW_TO);
    NEED_PARSE_TOPICS.put(TRANSFER_TOPICS, TRANSFER);
    NEED_PARSE_TOPICS.put(NEW_HEADER_BLOCK_TOPICS, NEW_HEADER_BLOCK);
    NEED_PARSE_TOPICS.put(EXITED_ETHER_TOPICS, EXITED_ETHER);
    NEED_PARSE_TOPICS.put(EXIT_TOKEN_TO_TOPICS, EXIT_TOKEN_TO);
    NEED_PARSE_TOPICS.put(EXITED_ERC20_TOPICS, EXITED_ERC20);
    NEED_PARSE_TOPICS.put(EXITED_MINTABLE_ERC20_TOPICS, EXITED_MINTABLE_ERC20);
    NEED_PARSE_TOPICS.put(RELAY_EXIT_REFUEL_TOPICS, RELAY_EXIT);

  }

  /**
   * Convert millisecond timestamp to String date format
   *
   * @param timestamp milliseconds
   */
  public static String convertMillSecondsToDay(long timestamp) {
    SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT_MATCHER);
    format.setTimeZone(TimeZone.getTimeZone(GMT8_MATCHER));
    return format.format(new Date(timestamp));
  }

  /**
   * Convert String date to Date. Based on UTC+0
   */
  public static Date convertStringDayToDate(String day) {
    SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT_MATCHER);
    format.setTimeZone(TimeZone.getTimeZone(GMT8_MATCHER));
    Date date = null;
    try {
      date = format.parse(day);
    } catch (ParseException e) {
      e.printStackTrace();
    }
    return date;
  }

  /**
   * Convert millisecond timestamp to Date. Based on UTC+0
   */
  public static Date convertTimeStampToDate(long timestamp) {
    SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT_MATCHER);
    format.setTimeZone(TimeZone.getTimeZone(GMT8_MATCHER));
    String time = format.format(new Date(timestamp));
    Date date = null;
    try {
      date = format.parse(time);
    } catch (ParseException e) {
      e.printStackTrace();
    }
    return date;
  }

  public static boolean isTimeout(Date date, int timeout) {
    return System.currentTimeMillis() - date.getTime()
        > ONE_MINUTE_IN_MILLS * timeout;
  }

  @SuppressWarnings("squid:S112")
  private static int toDigit(char ch, int index) {
    int digit = Character.digit(ch, 16);
    if (digit == -1) {
      throw new RuntimeException("Illegal hexadecimal character " + ch + " at index " + index);
    }
    return digit;
  }

  @SuppressWarnings({"squid:S127", "squid:S112"})
  public static String fromHex(String hex) {
    hex = hex.replace("\\x", "");
    char[] data = hex.toCharArray();
    int len = data.length;
    if ((len & 0x01) != 0) {
      throw new RuntimeException("The number of characters should be an even number.");
    }
    byte[] out = new byte[len >> 1];
    for (int i = 0, j = 0; j < len; i++) {
      int f = toDigit(data[j], j) << 4;
      j++;
      f |= toDigit(data[j], j);
      j++;
      out[i] = (byte) (f & 0xFF);
    }
    return new String(out);
  }

}
