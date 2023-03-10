package org.bttc.relayer.constant;

/**
 * @Author: tron
 * @Date: 2022/2/10 3:22 PM
 */
public class CommonConstant {

  private CommonConstant() {
  }

  /**
   * 41 + address
   */
  public static final byte ADD_PRE_FIX_BYTE_MAINNET = (byte) 0x41;
  public static final int ADDRESS_SIZE = 21;

  public static final long ONE_MINUTE_IN_MILLS = 60 * 1000L;

  public static final String SUCCESS = "SUCCESS";
  public static final String FAIL = "FAIL";

  public static final int RETURN_SUCCESS = 0;
  public static final int RETURN_FAIL = -1;
  public static final int RETURN_IGNORE = 2;

  public static final int TRON_RETRY_TIMES = 3;
  public static final int ETH_RETRY_TIMES = 5;
  public static final int BSC_RETRY_TIMES = 3;
  public static final int RETRY_TIMES = 5;

  public static final int CONTROLLER_RETRY_TIMES = 2;

  public static final int ABI_HEAD_OR_TAIL_LENGTH_IN_BYTE = 32;
  public static final int DATA_APPEND_LENGTH = 64;

  public static final  String DEFAULT_TRON_WITHDRAW_GAS_LIMIT = "250000";
  public static final String DEFAULT_ETH_WITHDRAW_GAS_LIMIT = "600000";
  public static final String DEFAULT_BSC_WITHDRAW_GAS_LIMIT = "600000";

  // para name of the rpc node interfaces
  public static final String RESULT = "result";
  public static final String TRANSACTION_HASH = "transactionHash";
  public static final String BLOCK_NUMBER = "blockNumber";
  public static final String  TOPICS = "topics";
  public static final String JSON_RPC = "jsonrpc";
  public static final String METHOD = "method";
  public static final String ETH_CALL = "eth_call";
  public static final String LATEST = "latest";
  public static final String PARAMS = "params";
  public static final String ADDRESS = "address";
  public static final String FROM_BLOCK = "fromBlock";
  public static final String TO_BLOCK = "toBlock";
  public static final String ERROR = "error";
  public static final String HEX_PREFIX_MATCHER = "^0[x|X]";

  //para name of trongrid interface
  public static final String TRON_BLOCK_NUMBER = "block_number";
  public static final String TRON_EVENT_NAME = "event_name";
  public static final String TRON_LINK = "links";
  public static final String TRON_TRANSACTION_ID = "transaction_id";
  public static final String TRON_CONTRACT_ADDRESS = "contract_address";
  public static final String TRON_VALUE = "value";
  public static final String TRON_RAW_DATA = "raw_data";
  public static final String TRON_PARAMETER = "parameter";
  public static final String TRON_OWNER_ADDRESS = "owner_address";
  public static final String TRON_FUNCTION_SELECTOR = "function_selector";
  public static final String TRON_VISIBLE = "visible";
  public static final String TRON_CONSTANT_RESULT = "constant_result";
  public static final String TRON_BLOCK_HEADER = "block_header";
  public static final String TRON_RESULT = "result";
  public static final String TRON_META = "meta";
}
