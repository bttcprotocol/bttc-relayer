package org.bttc.relayer.schedule.strategy.impl.bttc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.base.Throwables;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.bttc.relayer.bean.dao.MessageCenter;
import org.bttc.relayer.bean.dao.TokenMap;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.client.SlackClient;
import org.bttc.relayer.config.AddressConfig;
import org.bttc.relayer.constant.CommonConstant;
import org.bttc.relayer.schedule.Util;
import org.bttc.relayer.schedule.strategy.BttcParseDataStrategy;
import org.bttc.relayer.service.MessageCenterConfigService;
import org.bttc.relayer.service.TokenMapService;
import org.bttc.relayer.service.TransactionsService;
import org.bttc.relayer.utils.HttpClientUtil;
import org.bttc.relayer.utils.MathUtils;
import org.springframework.stereotype.Service;

/**
 * @Author: tron
 * @Date: 2022/2/21
 */
@Slf4j
@Service("BttcBttcParseData")
public class BttcParseData implements BttcParseDataStrategy {

  private static final int CHAIN_ID = ChainTypeEnum.BTT.code;
  private static final long BLOCK_DELAY = 300;  //the blocks count generated in 10 minutes

  private static final String GET_LOGS_METHOD = "eth_getLogs";

  private AddressConfig addressConfig;
  private TransactionsService transactionsService;
  private TokenMapService tokenMapService;
  private MessageCenterConfigService messageCenterConfigService;

  private String bttcUrlPrefix;

  private List<String> childERC20Exit;
  private List<String> withdrawTopics = new ArrayList<>(2);
  private List<List<String>> withdrawTopicsList = new ArrayList<>(1);

  // relayer address
  private String relayerAddress;

  @Resource
  private SlackClient slackClient;


  public BttcParseData(AddressConfig addressConfig,
      TransactionsService transactionsService,
      TokenMapService tokenMapService,
      MessageCenterConfigService messageCenterConfigService) {
    this.addressConfig = addressConfig;
    this.transactionsService = transactionsService;
    this.tokenMapService = tokenMapService;
    this.messageCenterConfigService = messageCenterConfigService;
  }

  @PostConstruct
  public void init() {
    bttcUrlPrefix = addressConfig.getBttcUrlPrefix();
    withdrawTopics.add(Util.RELAY_EXIT_REFUEL_TOPICS);
    withdrawTopicsList.add(withdrawTopics);
    relayerAddress = addressConfig.getRelayerBttcAddress();
    // childERC20Exit
    String childERC20ExitStr = addressConfig.getChildERC20Exit();
    if (StringUtils.isBlank(childERC20ExitStr)) {
      String msg = "BttcParseData initialize failed, childERC20ExitStr is blank";
      log.error(msg);
      slackClient.sendTextMessage(msg);
      System.exit(1);
    } else {
      childERC20Exit = Arrays.asList(childERC20ExitStr.split(";"));
    }
  }

  @Override
  @SuppressWarnings("squid:S3776")
  public long parseBttcWithdraw(long fromBlockNum, boolean confirm, boolean addData) {
    long toBlockNum = getToBlockNumber(fromBlockNum, confirm, addData);
    if (toBlockNum == 0L) {
      return 0L;
    }

    boolean parseError = false;
    long eventBlockNum = fromBlockNum - 1;
    List<MessageCenter> messageList = new ArrayList<>();
    JSONObject result;
    result = getMultiTopicsLogs(fromBlockNum,
        toBlockNum,
        childERC20Exit,
        withdrawTopicsList);
    if (MapUtils.isEmpty(result)) {
      // didn't get the lgo, return and retry later
      return 0L;
    }

    JSONArray logArray = result.getJSONArray(CommonConstant.RESULT);
    if (CollectionUtils.isEmpty(logArray)) {
      return toBlockNum;
    }

    for (int i = 0; i < logArray.size(); i++) {
      JSONObject logsObj = logArray.getJSONObject(i);
      if (addData) {
        String srcHash = logsObj.getString(CommonConstant.TRANSACTION_HASH);
        QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("src_txid", srcHash)
            .eq("src_chain_id", ChainTypeEnum.BTT.code);
        Transactions tx = transactionsService.getBaseMapper().selectOne(queryWrapper);
        if (ObjectUtils.isNotEmpty(tx)) {
          continue;
        }
      }
      int resultValue = parseBttcWithdrawLog(logsObj, messageList, confirm);
      if (resultValue == CommonConstant.RETURN_FAIL) {
        parseError = true;
        break;
      }
    } //_for

    // If there is no error in the whole parsing process, store the parsing result in the database
    if (!parseError) {
      if (!messageList.isEmpty()) {
        saveBttcWithdraw(messageList, confirm);
      }
      for (MessageCenter messageCenter : messageList) {
        eventBlockNum = Math.max(eventBlockNum, messageCenter.getBlockNumber());
      }

      return getParseToBlock( eventBlockNum,  fromBlockNum,  toBlockNum,
          confirm,  addData, messageList.size(), "bttc withdraw");
    } else {
      log.info(
          "bttc withdraw: something wrong, the from block is {}, to block is {}, confirm is {}, "
              + "transaction number is {}, addData is {}", fromBlockNum, toBlockNum, confirm,
          messageList.size(), addData);
      // parse error, return 0 and retry later
      return 0L;
    }
  }

  private int parseBttcWithdrawLog(
      JSONObject logsObj, List<MessageCenter> messageList, boolean confirm) {
    MessageCenter message = getBasicsMessageFromEvent(logsObj, confirm);
    if (ObjectUtils.isEmpty(message)) {
      return CommonConstant.RETURN_FAIL;
    }
    JSONArray topicsArray = logsObj.getJSONArray(CommonConstant.TOPICS);
    String fromAddress;
    String toAddress;
    BigInteger amount;
    BigInteger fee;
    BigInteger tsfFee;
    if (Util.RELAY_EXIT_REFUEL_TOPICS.equalsIgnoreCase(topicsArray.getString(0))) {
      fromAddress = getFromAddressForWithdraw(logsObj.getString(CommonConstant.TRANSACTION_HASH));
      if (StringUtils.isBlank(fromAddress)) {
        log.error("parseBttcWithdraw error, can not get the transaction receipt, txid: {}",
            logsObj.getString(CommonConstant.TRANSACTION_HASH));
        return CommonConstant.RETURN_FAIL;
      }
      String relayer = "0x"
          + topicsArray.getString(2).substring(topicsArray.getString(2).length() - 40);
      // filter the transactions that you designate yourself to assist in receiving
      if (this.relayerAddress.equalsIgnoreCase(relayer)) {
        toAddress = "0x" + logsObj.getString("data").substring(2 + 64 - 40, 2 + 64);
        String fromToken =
            "0x" + logsObj.getString("data").substring(2 + 64 * 3 - 40, 2 + 64 * 3);

        // modify fromToken
        if (message != null) {
          message.setTokenId(fromToken);
        }
        amount = new BigInteger(MathUtils.convertTo10RadixInString(
            logsObj.getString("data").substring(2 + 64 * 3, 2 + 64 * 4)));
        fee = new BigInteger(MathUtils.convertTo10RadixInString(
            logsObj.getString("data").substring(2 + 64 * 4, 2 + 64 * 5)));
        tsfFee = new BigInteger(MathUtils.convertTo10RadixInString(
            logsObj.getString("data").substring(2 + 64 * 6, 2 + 64 * 7)));
      } else {
        return CommonConstant.RETURN_IGNORE;
      }
    } else {
      return CommonConstant.RETURN_FAIL;
    }

    if (message != null) {
      message.setFromAddress(fromAddress);
      message.setToAddress(toAddress);
      message.setAmount(amount.toString());
      message.setFee(fee.toString());
      message.setTsfFee(tsfFee.toString());

      messageList.add(message);
      log.info("Bttc withdraw message is about to save into db, txid: {}, confirm: {}",
          message.getTxId(), confirm);
    }

    return CommonConstant.RETURN_SUCCESS;
  }

  private int saveBttcWithdraw(List<MessageCenter> messageList, boolean confirm) {
    if (CollectionUtils.isNotEmpty(messageList)) {
      for (MessageCenter message : messageList) {
        int num = transactionsService.parseSideChainWithdrawMessage(message, confirm);
        if (confirm && (num <= 0)) {
          String msg = String.format("[ALERT]_Bttc withdraw transaction insert into db error, "
              + "txid: %d, return num:  %s", message.getTxId(), num);
          log.error(msg);
          slackClient.sendTextMessage(msg);
        }
      }
    }

    return CommonConstant.RETURN_SUCCESS;
  }

  @Override
  public long getBlockNumber(boolean confirm) {
    JSONObject map = new JSONObject();
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_blockNumber");
    map.put("id", 1);
    String resp = HttpClientUtil.doPostJson(bttcUrlPrefix, map.toJSONString());
    if (StringUtils.isBlank(resp)) {
      log.error("eth_blockNumber error, json: {}", map.toJSONString());
      return 0L;
    }

    if (!JSON.parseObject(resp).containsKey(CommonConstant.RESULT)
        || JSON.parseObject(resp).get(CommonConstant.RESULT) == null) {
      log.error("eth_blockNumber error, json: {}", map.toJSONString());
      return 0L;
    }

    long blockNumber = MathUtils.convertTo10Radix(JSON.parseObject(resp).getString(CommonConstant.RESULT));
    if (confirm) {
      blockNumber -= 64;
    }
    return blockNumber;
  }

  private JSONObject getMultiTopicsLogs(long fromBlock, long toBlock,
      List<String> contractAddressList, List<List<String>> topicList) {
    JSONObject map = new JSONObject();
    JSONObject paramObj = new JSONObject();
    JSONArray params = new JSONArray();
    paramObj.put(CommonConstant.FROM_BLOCK, "0x" + Long.toHexString(fromBlock));
    paramObj.put(CommonConstant.TO_BLOCK, "0x" + Long.toHexString(toBlock));
    paramObj.put(CommonConstant.ADDRESS, contractAddressList);
    paramObj.put(CommonConstant.TOPICS, topicList);
    params.add(paramObj);
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, GET_LOGS_METHOD);
    map.put(CommonConstant.PARAMS, params);
    map.put("id", 1);

    for (int i = 0; i < Util.RETRY_TIMES; ++i) {
      try {
      String resp = HttpClientUtil.doPostJson(bttcUrlPrefix, map.toJSONString());
      if (StringUtils.isNotBlank(resp)) {
        JSONObject logsObj = JSON.parseObject(resp);
        if (logsObj.containsKey(CommonConstant.RESULT)) {
          return logsObj;
        }
      }
      Thread.sleep(1000);
      } catch (InterruptedException e) {
        log.warn("getMultiTopicsLogs fail, the exception is {}",
            Throwables.getStackTraceAsString(e));
        Thread.currentThread().interrupt();
      }
    }

    log.error("bttc get logs error, json rpc call return blank, url: {}, json: {}",
        bttcUrlPrefix, map.toJSONString());
    return new JSONObject();
  }

  public long getBlockTimestamp(String blockNumHex) {
    JSONObject map = new JSONObject();
    JSONArray params = new JSONArray();
    params.add(blockNumHex);
    params.add(false);
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_getBlockByNumber");
    map.put(CommonConstant.PARAMS, params);
    map.put("id", 1);

    String blockResp = HttpClientUtil.doPostJson(bttcUrlPrefix, map.toJSONString());
    if (StringUtils.isBlank(blockResp)) {
      log.warn("btt get eth_getBlockByNumber null, the block num is {}, "
          + "return blockResp is {}", blockNumHex, blockResp);
      return 0L;
    }

    JSONObject respObj;
    try {
      respObj = JSON.parseObject(blockResp);
      if (respObj.containsKey(CommonConstant.RESULT)) {
        JSONObject result = respObj.getJSONObject(CommonConstant.RESULT);
        if (MapUtils.isNotEmpty(result) && result.containsKey("timestamp")) {
          String timeStamp = result.getString("timestamp");
          if (StringUtils.isNotBlank(timeStamp)) {
            return MathUtils.convertTo10Radix(timeStamp);
          }
        }
      }
    } catch (Exception e) {
      log.warn("btt get eth_getBlockByNumber error, the block num is {}, "
          + "return blockResp is {}", blockNumHex, blockResp);
      throw e;
    }
    log.warn("btt get eth_getBlockByNumber error, the block num is {}, "
        + "return value is {}", blockNumHex, respObj);
    return 0;
  }

  private Map<String, TokenMap> getTokenMaps() {
    List<TokenMap> tokenMapList = tokenMapService.getTokenMaps(0);
    Map<String, TokenMap> allTokenMap = new HashMap<>(tokenMapList.size());
    for (TokenMap tokenMap : tokenMapList) {
      allTokenMap.put(tokenMap.getChildAddress().toLowerCase(), tokenMap);
    }
    return allTokenMap;
  }

  private String getFromAddressForWithdraw(String txid) {
    for (int i = 0; i < Util.RETRY_TIMES; ++i) {
      try {
      JSONObject result = getTransactionReceipt(txid);
      if (MapUtils.isNotEmpty(result)) {
        return result.getString("from");
      }
      Thread.sleep(1000);
      } catch (InterruptedException e) {
        log.warn("GetFromAddressForWithdraw fail, the exception is {}",
            Throwables.getStackTraceAsString(e));
        Thread.currentThread().interrupt();
      }
    }
    return null;
  }

  @Override
  public JSONObject getTransactionByHash(String hash, boolean confirm) {
    JSONArray params = new JSONArray();
    params.add(hash);

    JSONObject map = new JSONObject();
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_getTransactionByHash");
    map.put(CommonConstant.PARAMS, params);
    map.put("id", 1);

    String maxBlockResp = HttpClientUtil.doPostJsonRetry(
        bttcUrlPrefix, map.toJSONString(), Util.RETRY_TIMES);
    try {
      JSONObject respObj = JSON.parseObject(maxBlockResp);
      return respObj.getJSONObject(CommonConstant.RESULT);
    } catch (Exception e) {
      log.warn("bttc get transaction by hash fail, the hash is {}, the exception is {}",
          hash, Throwables.getStackTraceAsString(e));
      return new JSONObject();
    }
  }

  public JSONObject getTransactionReceipt(String txid) {
    JSONArray params = new JSONArray();
    params.add(txid);
    JSONObject map = new JSONObject();
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_getTransactionReceipt");
    map.put("id", 1);
    map.put(CommonConstant.PARAMS, params);
    String resp = HttpClientUtil.doPostJson(bttcUrlPrefix, map.toJSONString());
    if (StringUtils.isBlank(resp)) {
      log.error("getTransactionReceipt error");
      return new JSONObject();
    }

    if (!JSON.parseObject(resp).containsKey(CommonConstant.RESULT)
        || JSON.parseObject(resp).get(CommonConstant.RESULT) == null) {
      return new JSONObject();
    }

    return JSON.parseObject(resp).getJSONObject(CommonConstant.RESULT);
  }

  private long getToBlockNumber(long fromBlockNum, boolean confirm, boolean addData) {
    long toBlockNum;
    long chainBlockNum = messageCenterConfigService.getBlockNumber(CHAIN_ID, confirm);
    if (chainBlockNum > 0) {
      if (addData) {
        chainBlockNum -= BLOCK_DELAY;
      }
      toBlockNum = Math.min(fromBlockNum + Util.BLOCK_RANGE, chainBlockNum);
      if (toBlockNum <= fromBlockNum) {
        return 0L;
      }
    } else {
      return 0L;
    }

    return toBlockNum;
  }

  private MessageCenter getBasicsMessageFromEvent(JSONObject logsObj, boolean confirm) {
    JSONArray topicsArray = logsObj.getJSONArray(CommonConstant.TOPICS);
    String eventSign = topicsArray.getString(0);

    MessageCenter message = new MessageCenter();
    message.setTxId(logsObj.getString(CommonConstant.TRANSACTION_HASH));
    long logBlockNumber = MathUtils.convertTo10Radix(logsObj.getString(CommonConstant.BLOCK_NUMBER));
    message.setBlockNumber(logBlockNumber);
    message.setContractAddress(logsObj.getString(CommonConstant.ADDRESS));
    message.setEventType(Util.getNeedParseTopics().get(eventSign));

    Map<String, TokenMap> tokenMaps = getTokenMaps();
    String tokenId;
    if (Util.RELAY_EXIT_REFUEL_TOPICS.equalsIgnoreCase(eventSign)) {
      tokenId = "0x" + logsObj.getString("data").substring(2 + 64 * 3 - 40, 2 + 64 * 3);
    } else {
      tokenId = logsObj.getString(CommonConstant.ADDRESS);
      message.setTokenId(tokenId);
    }

    TokenMap tokenMap = tokenMaps.get(tokenId.toLowerCase());
    if (ObjectUtils.isEmpty(tokenMap)) {
      log.error("Get token info error, txid: {}, child token address: {}",
          message.getTxId(), logsObj.getString(CommonConstant.ADDRESS));
      return null;
    }
    message.setFromChainId("bttc");
    int chainId = tokenMap.getChainId();
    if (chainId == 1) {
      message.setToChainId("tron");
    } else if (chainId == 2) {
      message.setToChainId("eth");
    } else if (chainId == 3) {
      message.setToChainId("bsc");
    } else {
      String msg = String.format("Bttc parse withdraw error, invalid chain id: %d, txid: %s",
          chainId, message.getTxId());
      log.error(msg);
      slackClient.sendTextMessage(msg);
      return null;
    }

    int status = 0;
    // removed: TAG - true when the log was removed, due to a chain reorganization.
    //          false if its a valid log.
    if (confirm) {
      status = "false".equalsIgnoreCase(logsObj.getString("removed")) ? 1 : 2;
    }
    message.setStatus(status);
    message.setEventIndex((int) MathUtils.convertTo10Radix(logsObj.getString("logIndex")));
    message.setContent(logsObj.toJSONString());
    long blockTimestamp = getBlockTimestamp(logsObj.getString(CommonConstant.BLOCK_NUMBER));
    if (blockTimestamp != 0) {
      message.setTimeStamp(Util.convertMillSecondsToDay(blockTimestamp * 1000));
    } else {
      return null;
    }

    return message;
  }

  //In order to prevent the problem of missing transactions caused by the slow synchronization of the nodes accessed in this function,
  // the logic of the latest block number parsed is updated as follows:
  //1. If the relevant event is resolved in this round, the latest block number parsed is the largest block number that actually generated the relevant event.
  //2. If no relevant event is resolved in this round, then:
  // 2.1) When toBlockNum - fromBlockNum < 400, the latest block number parsed will not be updated
  // 2.2) Otherwise, the latest block number parsed is advanced by 100 blocks. To prevent the fact that there is no exit event within 500 blocks, causing the parsing task to fail to move forward
  private long getParseToBlock(long eventBlockNum, long fromBlockNum, long toBlockNum,
      boolean confirm, boolean add, int txNumber, String taskName) {
    if (eventBlockNum >= fromBlockNum) {
      log.info(taskName + ": the from block is {}, to block is {}, confirm is {}, "
              + "add is {}, transaction number is {}", fromBlockNum, eventBlockNum, confirm,
          add, txNumber);
      return eventBlockNum;
    } else if (toBlockNum - fromBlockNum < Util.BLOCK_RANGE - 100) {
      log.info(taskName + ": there is no events in less than {} blocks, from {} to {},"
          + "confirm is {}, add is {}", Util.BLOCK_RANGE - 100, fromBlockNum, toBlockNum, confirm, add);
      return fromBlockNum - 1;
    } else {
      log.info(taskName + ": there is no events in {} blocks, from {} to {}, confirm is {}, add is {}",
          Util.BLOCK_RANGE, fromBlockNum, toBlockNum, confirm, add);
      return fromBlockNum - 1 + 100;
    }
  }
}
