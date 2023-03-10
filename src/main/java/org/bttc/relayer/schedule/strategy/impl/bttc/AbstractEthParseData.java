package org.bttc.relayer.schedule.strategy.impl.bttc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.base.Throwables;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.bttc.relayer.bean.dao.CheckPointInfo;
import org.bttc.relayer.bean.dao.MessageCenter;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.bean.enums.StatusEnum;
import org.bttc.relayer.client.Client;
import org.bttc.relayer.client.SlackClient;
import org.bttc.relayer.config.AddressConfig;
import org.bttc.relayer.constant.CommonConstant;
import org.bttc.relayer.mapper.CheckPointInfoMapper;
import org.bttc.relayer.schedule.Util;
import org.bttc.relayer.schedule.strategy.BttcParseDataStrategy;
import org.bttc.relayer.service.MessageCenterConfigService;
import org.bttc.relayer.service.TokenMapService;
import org.bttc.relayer.service.TransactionsService;
import org.bttc.relayer.utils.ContractUtil;
import org.bttc.relayer.utils.MathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;

/**
 * @Author: tron
 * @Date: 2022/2/24
 */
@Service
@Slf4j
public class AbstractEthParseData implements BttcParseDataStrategy {

  private static final String STATUS_SUCCESS = "0x1";

  // This is the cost to send Ether between parties
  private static final BigInteger gasPriceIncrement = BigInteger.valueOf(3);

  //block count generated in about 30 minutes
  private static final long BSC_BLOCK_DELAY = 600;
  private static final long ETH_BLOCK_DELAY = 130;
  protected int chainId;

  protected Client client;

  protected String rootChainManagerProxy;
  protected String rootChainProxy;
  protected String etherPredicateProxy;
  protected String erc20PredicateProxy;
  protected String mintableERC20PredicateProxy;
  // the map of tokenPredicateProxy contract addresses and topic
  protected Map<String, String> tokenPredicateProxyToEventMap;
  // tokenPredicateProxy contract addresses
  protected List<String> tokenPredicateProxyList;
  // erc20PredicateProxy contract addresses
  protected List<String> erc20PredicateProxyList;
  // tokenPredicateProxy contract addresses
  protected List<List<String>> exitedErc20EventTopicsList;
  protected String chainTokenAddress;
  // default GasPrice
  protected String defaultGasPrice;
  protected BigInteger defaultGasPriceLimit;
  protected BigInteger defaultGasLimit;

  private String relayerAddress;
  private Credentials credentials;

  @Autowired
  private TransactionsService transactionsService;

  @Autowired
  private TokenMapService tokenMapService;

  @Autowired
  private MessageCenterConfigService messageCenterConfigService;

  @Autowired
  private AddressConfig addressConfig;

  @Resource
  private CheckPointInfoMapper checkpointInfoMapper;
  @Resource
  private SlackClient slackClient;

  private int createCredentials() {
    synchronized (AbstractEthParseData.class) {
      credentials = Credentials.create(addressConfig.getRelayerKey());
      relayerAddress = credentials.getAddress();
    }
    return CommonConstant.RETURN_SUCCESS;
  }

  @Override
  public long parseMainChainCheckpoint(long fromBlockNum, boolean confirm) {
    long toBlockNum = getToBlockNumber(fromBlockNum, confirm, false);
    if (toBlockNum == 0L) {
      return 0L;
    }
    long eventBlockNum = fromBlockNum - 1;
    JSONObject result = getLogsWithRetry(fromBlockNum,
        toBlockNum,
        Collections.singletonList(rootChainProxy),
        Collections.singletonList(Util.NEW_HEADER_BLOCK_TOPICS));
    if (MapUtils.isEmpty(result)) {
      return 0L;
    }

    boolean parseError = false;
    List<CheckPointInfo> checkPointInfoList = new ArrayList<>();
    if (!result.getJSONArray(CommonConstant.RESULT).isEmpty()) {
      for (int i = 0; i < result.getJSONArray(CommonConstant.RESULT).size(); i++) {
        JSONObject logsObj = result.getJSONArray(CommonConstant.RESULT).getJSONObject(i);
        long blockNum = Long.parseLong(logsObj.getString(CommonConstant.BLOCK_NUMBER).substring(2), 16);
        eventBlockNum = Math.max(eventBlockNum, blockNum);
        // CheckPointInfo
        int resultValue = parseCheckPointLog(logsObj, checkPointInfoList, rootChainProxy, confirm);
        if (resultValue != CommonConstant.RETURN_SUCCESS) {
          parseError = true;
          break;
        }
      } //_for
    } //_if
    if (!parseError) {
      int resultValue = saveCheckPointInfo(checkPointInfoList, confirm);
      if (resultValue != CommonConstant.RETURN_SUCCESS) {
        return 0L;
      }
      return getParseToBlock( eventBlockNum,  fromBlockNum,  toBlockNum,
          confirm,  false, checkPointInfoList.size(), "main chain checkpoint");
    }
    // parseError is true, return 0 and retry later
    return 0L;
  }

  private int parseCheckPointLog(JSONObject logsObj, List<CheckPointInfo> checkPointInfoList,
      String contractAddress, boolean confirm) {
    if (!rootChainProxy.equalsIgnoreCase(contractAddress)) {
      log.error("get checkPointNum fail, wrong contractAddress is {}, the data is {}",
          contractAddress, logsObj);
      return CommonConstant.RETURN_FAIL;
    }
    CheckPointInfo checkPointInfo = new CheckPointInfo();
    String txid = logsObj.getString(CommonConstant.TRANSACTION_HASH);
    checkPointInfo.setTxId(txid);
    long logBlockNumber = MathUtils.convertTo10Radix(logsObj.getString(CommonConstant.BLOCK_NUMBER));
    checkPointInfo.setBlockNumber(logBlockNumber);

    checkPointInfo.setChainId(ChainTypeEnum.fromCode(chainId).code);
    String ret = CommonConstant.SUCCESS;
    // removed: TAG - true when the log was removed, due to a chain reorganization.
    //          false if its a valid log.
    if (!"false".equalsIgnoreCase(logsObj.getString("removed"))) {
      ret = CommonConstant.FAIL;
    }
    checkPointInfo.setResult(ret);
    long blockTimestamp = getBlockTimestamp(logsObj.getString(CommonConstant.BLOCK_NUMBER));
    if (blockTimestamp != 0) {
      checkPointInfo.setTimeStamp(Util.convertTimeStampToDate(blockTimestamp * 1000));
    } else {
      log.warn("get block time fail, the chain is {}, the txid is {}", chainId, txid);
      return CommonConstant.RETURN_FAIL;
    }
    // NewHeaderBlock (index_topic_1 address proposer, index_topic_2 uint256 headerBlockId,
    //                 index_topic_3 uint256 reward, uint256 start, uint256 end, bytes32 root)
    long startBlock = MathUtils.convertTo10Radix(
        logsObj.getString("data").substring(2, 2 + 64));

    long endBlock = MathUtils.convertTo10Radix(
        logsObj.getString("data").substring(2 + 64, 2 + 64 * 2));

    List<String> topicList = JSON.parseArray(
        logsObj.getString(CommonConstant.TOPICS), String.class);
    String checkPointNum = ContractUtil.getNumberFromEthHex(topicList.get(2));
    if (StringUtils.isNotBlank(checkPointNum) && checkPointNum.length() > 4) {
      checkPointNum = checkPointNum.substring(0, checkPointNum.length() - 4);
    } else {
      log.warn("get checkPointNum fail, the txid is {}, the logsObj is {}", txid, logsObj);
      return CommonConstant.RETURN_FAIL;
    }
    checkPointInfo.setCheckPointNum(Long.parseLong(checkPointNum));

    checkPointInfo.setStartBlock(startBlock);
    checkPointInfo.setEndBlock(endBlock);

    checkPointInfoList.add(checkPointInfo);
    int confirmStatus = 0;
    if (confirm) {
      confirmStatus = 1;
    }
    checkPointInfo.setConfirm(confirmStatus);
    log.info("main chain {}: checkpoint message is about to save into db, txid: {}, confirm: {}",
        chainId, checkPointInfo.getTxId(), confirm);
    return CommonConstant.RETURN_SUCCESS;
  }

  private int saveCheckPointInfo(List<CheckPointInfo> checkPointInfoList, boolean confirm) {
    if (checkPointInfoList.isEmpty()) {
      return CommonConstant.RETURN_SUCCESS;
    }

    if (confirm) {
      int num = checkpointInfoMapper.insertConfirmMsg(checkPointInfoList);
      if (num < checkPointInfoList.size()) {
        log.error("[ALERT]_Main chain {} checkpoint message insert into db error, return num: {}",
            chainId, num);
        return CommonConstant.RETURN_FAIL;
      }
    } else {
      checkpointInfoMapper.insertUnConfirmMsg(checkPointInfoList);
    }
    return CommonConstant.RETURN_SUCCESS;
  }

  @Override
  @SuppressWarnings("squid:S3776")
  public int parseMainChainSubmitWithdraw(Transactions tx, String payload,
      boolean normal) {
    if (ObjectUtils.isEmpty(credentials) || StringUtils.isBlank(relayerAddress)) {
      createCredentials();
    }

    byte[] inputDataByte = Numeric.hexStringToByteArray(payload);
    DynamicBytes inputData = new DynamicBytes(inputDataByte);

    Function function = new Function(
        "exit",
        Arrays.asList(inputData),
        Collections.emptyList());
    String nonceStr = getAccountLastNonce(relayerAddress, normal);
    BigInteger nonce;
    if (StringUtils.isNotBlank(nonceStr)) {
      nonce = new BigInteger(nonceStr);
      if (!normal) {
        BigInteger lastNonce = new BigInteger(tx.getNonce());
        if (nonce.compareTo(lastNonce) < 0) {
          nonce = lastNonce;
        }
      }
    } else {
      String msg = String.format("Get account nonce return null, chain is %d, account is %s",
          chainId, relayerAddress);
      log.error(msg);
      slackClient.sendTextMessage(msg);
      return CommonConstant.RETURN_FAIL;
    }
    String encodedFunction = FunctionEncoder.encode(function);

    Transaction gasEstimateTx = Transaction.createFunctionCallTransaction(
        relayerAddress,
        nonce,
        null,
        defaultGasLimit,
        rootChainManagerProxy,
        encodedFunction);
    EthEstimateGas gasEstimate = client.ethEstimateGas(gasEstimateTx);
    if (ObjectUtils.isEmpty(gasEstimate)) {
      String msg = String.format("GasEstimateTx return null, chain is %d, account is %s",
          chainId, relayerAddress);
      log.error(msg);
      slackClient.sendTextMessage(msg);
      return CommonConstant.RETURN_FAIL;
    } else if (gasEstimate.hasError()) {
      if (gasEstimate.getError().getMessage().contains(Util.EXIT_ALREADY_PROCESSED)) {
        // The asset has been received, just need to update the database transaction status
        transactionsService.parseMainChainSubmitWithdrawMessage(tx.getSrcTxid());
        String msg = String.format("Tx EXIT_ALREADY_PROCESSED, chain is %d, source hash is %s",
            chainId, tx.getSrcTxid());
        log.warn(msg);
        slackClient.sendTextMessage(msg);
        return CommonConstant.RETURN_SUCCESS;
      } else {
        String msg = String.format("Submit withdraw for tx %s main chain %d failed, msg: %s",
            tx.getSrcTxid(), chainId, gasEstimate.getError().getMessage());
        log.error(msg);
        slackClient.sendTextMessage(msg);
        return CommonConstant.RETURN_FAIL;
      }
    }

    // Get the real-time gas price and increase it by 10%-30% to speed up the on-chain.
    BigInteger gasPrice = new BigInteger(getGasPrice());
    if (normal) {
      gasPrice = gasPrice.add(gasPrice.divide(BigInteger.TEN));
    } else {
      gasPrice = gasPrice.add(gasPrice.divide(BigInteger.TEN).multiply(gasPriceIncrement));
    }
    // If the current gas price surges and exceeds the threshold,
    // the tx will not be submitted for the time being and retry later
    if (gasPrice.compareTo(defaultGasPriceLimit) > 0) {
      String msg = String.format("Current gas price %s exceed gas price limit %s",
          gasPrice, defaultGasPriceLimit.toString());
      log.error(msg);
      slackClient.sendTextMessage(msg);
      return CommonConstant.RETURN_FAIL;
    }

    RawTransaction rawTransaction = RawTransaction.createTransaction(
        nonce,
        gasPrice,
        gasEstimate.getAmountUsed().add(BigInteger.TEN),
        rootChainManagerProxy,
        encodedFunction);

    EthSendTransaction response =
        client.sendRawTransactionWithRetry(rawTransaction, credentials);
    String transactionHash;
    if (ObjectUtils.isNotEmpty(response) && StringUtils.isNotBlank(response.getTransactionHash())) {
      transactionHash = response.getTransactionHash();
      log.info("Submit withdraw for tx {} main chain {} tx hash {}, nonce = {} normal = {}",
          tx.getSrcTxid(), chainId, transactionHash, nonce, normal);
    } else {
      String msg = String.format("Submit withdraw for tx %s main chain %d failed",
          tx.getSrcTxid(), chainId);
      log.error(msg);
      slackClient.sendTextMessage(msg);
      return CommonConstant.RETURN_FAIL;
    }

    try {
      int num = transactionsService.parseMainChainSubmitWithdrawMessage(
          tx.getSrcTxid(), transactionHash, nonce.toString());
      if (num < 1) {
        String msg = String.format("Main chain %d withdraw message insert into db error, return num: %d",
            chainId, num);
        log.error(msg);
        slackClient.sendTextMessage(msg);
      }
    } catch (Exception e) {
      log.error("[ALERT]_Save main chain {} submit srcTxId {} withdraw throw exception {}",
          chainId, tx.getSrcTxid(), Throwables.getStackTraceAsString(e));
      String msg = String.format("Save main chain %d submit srcTxId %s withdraw failed",
          chainId, tx.getSrcTxid());
      slackClient.sendTextMessage(msg);
      Thread.currentThread().interrupt();
    }

    return CommonConstant.RETURN_SUCCESS;
  }

  @Override
  public long parseMainChainWithdraw(long fromBlockNum, boolean chainToken, boolean confirm, boolean add)
       {
    long toBlockNum = getToBlockNumber(fromBlockNum, confirm, add);
    long eventBlockNum = fromBlockNum - 1;
    if (toBlockNum == 0L) {
      return 0L;
    }

    JSONObject result = null;
    if (chainToken) {
      result = getLogsWithRetry(fromBlockNum,
          toBlockNum,
          Collections.singletonList(etherPredicateProxy),
          Collections.singletonList(Util.EXITED_ETHER_TOPICS));
    } else {
      result = getMultiTopicsLogs(fromBlockNum,
          toBlockNum,
          erc20PredicateProxyList,
          exitedErc20EventTopicsList);
    }
    if (MapUtils.isEmpty(result) || !result.containsKey(CommonConstant.RESULT)) {
      return 0L;
    }

    List<MessageCenter> messageList = new ArrayList<>();
    for (int i = 0; i < result.getJSONArray(CommonConstant.RESULT).size(); i++) {
      JSONObject logsObj = result.getJSONArray(CommonConstant.RESULT).getJSONObject(i);
      if (add) {
        String hash = logsObj.getString(CommonConstant.TRANSACTION_HASH);
        QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dest_txid", hash)
            .eq("dest_chain_id", chainId);
        Transactions tx = transactionsService.getBaseMapper().selectOne(queryWrapper);
        if (ObjectUtils.isNotEmpty(tx)) {
          continue;
        } else {
          log.info("main chain {} withdraw add tx: {}", chainId, hash);
        }
      }
      int resultValue = parseMainChainWithdrawLog(logsObj, messageList, confirm);
      if (resultValue != CommonConstant.RETURN_SUCCESS) {
        // parse error, return 0 and retry later
        log.warn(
            "main chain withdraw, chain id is {}: something wrong, the from block is {}, origin to block is {}, confirm is {}, "
                + "add is {}, transaction number is {}", chainId, fromBlockNum, toBlockNum, confirm, add,
            messageList.size());
        return 0L;
      }
    } //_for

    for (MessageCenter messageCenter : messageList) {
      eventBlockNum = Math.max(eventBlockNum, messageCenter.getBlockNumber());
    }

    return getParseToBlock( eventBlockNum,  fromBlockNum,  toBlockNum,
     confirm,  add, messageList.size(), "main chain withdraw");

  }

  private int parseMainChainWithdrawLog(
      JSONObject logsObj, List<MessageCenter> messageList, boolean confirm) {
    if (ObjectUtils.isEmpty(credentials) || StringUtils.isBlank(relayerAddress)) {
      createCredentials();
    }
    MessageCenter message = getBasicsMessageFromEvent(logsObj, confirm);
    if (ObjectUtils.isEmpty(message)) {
      return CommonConstant.RETURN_FAIL;
    }
    JSONArray topicsArray = logsObj.getJSONArray(CommonConstant.TOPICS);
    String eventSign = topicsArray.getString(0);
    String toAddress = "";
    BigInteger amount;
    String tokenId = "";
    if (eventSign.equalsIgnoreCase(Util.EXITED_ETHER_TOPICS)) {
      // ExitedEther(index_topic_1 exitor, uint256 amount)
      toAddress =
          "0x" + topicsArray.getString(1).substring(topicsArray.getString(1).length() - 40);
      // parse Data: "0x" + amount(uint256)
      amount = new BigInteger(MathUtils.convertTo10RadixInString(
          logsObj.getString("data").substring(2, 66)));
      tokenId = chainTokenAddress;
    } else if (eventSign.equalsIgnoreCase(Util.EXITED_ERC20_TOPICS) ||
        eventSign.equalsIgnoreCase(Util.EXITED_MINTABLE_ERC20_TOPICS)) {
      // ExitedERC20(address indexed withdrawer, address indexed rootToken, uint256 amount)
      // ExitedMintableERC20(address indexed withdrawer, address indexed rootToken, uint256 amount)
      toAddress =
          "0x" + topicsArray.getString(1).substring(topicsArray.getString(1).length() - 40);
      tokenId =
          "0x" + topicsArray.getString(2).substring(topicsArray.getString(2).length() - 40);
      // parse Data: "0x" + amount(uint256)
      amount = new BigInteger(MathUtils.convertTo10RadixInString(
          logsObj.getString("data").substring(2, 66)));
    } else {
      @SuppressWarnings("squid:S2259")
      String logHash = message.getTxId();
      log.warn("Main chain {} get logs topics error, hash: {}, event sig: {}",
          chainId, logHash, eventSign);
      return CommonConstant.RETURN_SUCCESS;
    }

    @SuppressWarnings("squid:S2259")
    JSONObject txReceipt = getTransactionReceipt(message.getTxId());
    String ownerAddress = null;
    if (MapUtils.isNotEmpty(txReceipt) && txReceipt.containsKey("from")) {
      ownerAddress = txReceipt.getString("from");
    }
    if (StringUtils.isBlank(ownerAddress)) {
      log.warn("parseMainChainWithdrawLog: chain {} get tx owner fail, tx is {}", chainId, message.getTxId());
      return CommonConstant.RETURN_FAIL;
    }
    message.setFromAddress(ownerAddress);
    message.setToAddress(toAddress);
    message.setAmount(amount.toString());
    message.setTokenId(tokenId);

    String sideChainHash = "";
    try {
      sideChainHash = transactionsService.getSideChainWithdrawTransactionHash(
          message.getTxId(), chainId);
    } catch (Exception e) {
      log.error("[ALERT]_Get side chain hash exception. mainChainHash = [{}], Exception = [{}]",
          message.getTxId(), Throwables.getStackTraceAsString(e));
      return CommonConstant.RETURN_FAIL;
    }
    if (StringUtils.isBlank(sideChainHash)) {
      log.error("[ALERT]_Get side chain hash error. mainChainHash = [{}]", message.getTxId());
      return CommonConstant.RETURN_FAIL;
    }
    int num = transactionsService.parseMainChainWithdrawMessage(
        message, sideChainHash, chainId);
    if (confirm && (num <= 0)) {
      String msg = String.format("Main chain %d withdraw transaction insert into db error, txid: %s, return num: %d",
          chainId, sideChainHash, num);
      log.error(msg);
      slackClient.sendTextMessage(msg);
    } else {
      log.info("Main chain {} withdraw message is about to save into db, txid: {}, confirm: {}",
          chainId, message.getTxId(), confirm);
    }
    messageList.add(message);
    return CommonConstant.RETURN_SUCCESS;
  }

  @Override
  @SuppressWarnings("squid:S3776")
  public int parseToChainData(Transactions tx, boolean confirm) {
    try {
      int status = tx.getTStatus();
      JSONObject txReceipt = getTransactionReceipt(tx.getDestTxid());
      if (MapUtils.isNotEmpty(txReceipt)) {
        String contractAddress = txReceipt.getString("to");
        if (rootChainManagerProxy.equalsIgnoreCase(contractAddress)) {
          if (STATUS_SUCCESS.equalsIgnoreCase(txReceipt.getString("status")) &&
              parseDestChainLog(tx, txReceipt)) {
              if (status != StatusEnum.DEST_CHAIN_HANDLED.getValue()) {
                tx.setDestContractRet(CommonConstant.SUCCESS);
                tx.setTStatus(StatusEnum.DEST_CHAIN_ON_CHAIN.getValue());
                tx.setUpdateTime(new Date());
                log.info(
                    "eth or bsc tx parseToChainData success, source hash: {}, status switch to {}",
                    tx.getSrcTxid(), tx.getTStatus());
              }
          } else {
            tx.setDestContractRet(CommonConstant.FAIL);
            tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLE_FAILED.getValue());
            tx.setUpdateTime(new Date());
            String msg = String.format("Main chain %d parseToChainData failed, "
                    + "tx failed, source hash: %s, dest hash: %s, status switch to %d",
                chainId, tx.getSrcTxid(), tx.getDestTxid(), tx.getTStatus());
            log.error(msg);
            slackClient.sendTextMessage(msg);
          }
        } else {
          tx.setTStatus(StatusEnum.DEST_CHAIN_HASH_ERROR.getValue());
          tx.setUpdateTime(new Date());
          String msg = String.format("Main chain %d parseToChainData failed, "
                  + "the contractAddress is not rootChainManagerProxy, "
                  + "source hash: %s, dest hash: %s, status switch to %d",
              chainId, tx.getSrcTxid(), tx.getDestTxid(), tx.getTStatus());
          log.error(msg);
          slackClient.sendTextMessage(msg);
        }

        long blockTimestamp = getBlockTimestamp(txReceipt.getString(CommonConstant.BLOCK_NUMBER));
        if (blockTimestamp != 0L) {
          tx.setDestTimestamp(new Date(blockTimestamp * 1000));
        } else {
          log.warn("eth or bsc tx {} get dest timestamp failed, dest hash: {}",
              tx.getSrcTxid(), tx.getDestTxid());
          tx.setDestTimestamp(new Date());
        }
      } else {
        // didn't get the tx info from chain, status turn to DEST_CHAIN_HANDLING
        tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLING.getValue());
      }
    } catch (Exception e) {
      log.warn("chain {} parseToChainData throw exception, src hash is {}, exception = [{}]",
          chainId, tx.getSrcTxid(), Throwables.getStackTraceAsString(e));
      tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLING.getValue());
    }

    return tx.getTStatus();
  }

  @Override
  public String getTransactionInput(String hash) {
    JSONArray params = new JSONArray();
    params.add(hash);

    JSONObject map = new JSONObject();
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_getTransactionByHash");
    map.put(CommonConstant.PARAMS, params);
    map.put("id", 1);

    String maxBlockResp = client.postRetry(map.toJSONString());
    JSONObject respObj = JSON.parseObject(maxBlockResp);
    return respObj.getJSONObject(CommonConstant.RESULT).getString("input");
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

    String maxBlockResp = client.postRetry(map.toJSONString());
    JSONObject respObj = JSON.parseObject(maxBlockResp);
    return respObj.getJSONObject(CommonConstant.RESULT);
  }

  @Override
  public long[] getCheckPointBlockNumber(long fromBlock) {
    // blockNumber[0]: main chain block number, blockNumber[1]: side chain block number
    long[] blockNumber = {0L, 0L};
    long toBlockNumber;

    // get the latest confirmed block number of eth/bsc
    long chainBlockNumber = messageCenterConfigService.getBlockNumber(chainId, true);
    if (chainBlockNumber > 0) {
      // parse up to Util.BLOCK_RANGE each time
      toBlockNumber = Math.min(fromBlock + Util.BLOCK_RANGE, chainBlockNumber);
      if (toBlockNumber <= fromBlock) {
        return blockNumber;
      }
    } else {
      // didn't get the latest confirmed block number, return and retry later
      return blockNumber;
    }

    JSONObject result = getLogsWithRetry(fromBlock,
        toBlockNumber,
        Collections.singletonList(rootChainProxy),
        Collections.singletonList(Util.NEW_HEADER_BLOCK_TOPICS));
    if (MapUtils.isEmpty(result)) {
      return blockNumber;
    }
    if (!result.getJSONArray(CommonConstant.RESULT).isEmpty()) {
      for (int i = 0; i < result.getJSONArray(CommonConstant.RESULT).size(); i++) {
        JSONObject logsObj = result.getJSONArray(CommonConstant.RESULT).getJSONObject(i);
        JSONArray topicsArray = logsObj.getJSONArray(CommonConstant.TOPICS);
        String eventSign = topicsArray.getString(0);
        if (eventSign.equalsIgnoreCase(Util.NEW_HEADER_BLOCK_TOPICS)) {
          // parse Data: "0x" + start(uint256) + end(uint256) + root(bytes32)
          long end = MathUtils.convertTo10Radix(
              logsObj.getString("data").substring(2 + 64, 2 + 64 * 2));
          if (blockNumber[1] < end) {
            blockNumber[1] = end;
          }
        }
      } //_for
    } //_if
    blockNumber[0] = toBlockNumber;

    return blockNumber;
  }

  @Override
  public long getBlockNumber(boolean confirm) {
    long blockNumber;
    if (confirm) {
      if (chainId == ChainTypeEnum.ETHEREUM.code) {
        blockNumber = getSolidityBlockNumber();
      } else {
        blockNumber = getLatestBlockNumber() - 16L;
      }
    } else {
      blockNumber = getLatestBlockNumber();
    }
    return blockNumber;
  }

  @Override
  public long getLastChildBlock() {
    long lastChildBlock = -1;
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.JSON_RPC, "2.0");
    jsonObject.put(CommonConstant.METHOD, CommonConstant.ETH_CALL);
    jsonObject.put("id", 1);
    JSONArray paramArr = new JSONArray();
    JSONObject paramObj = new JSONObject();
    paramObj.put("from", Util.ZERO_ADDRESS);
    paramObj.put("to", rootChainProxy);
    paramObj.put("data", Util.FUNC_GET_LAST_CHILD_BLOCK);
    paramArr.add(paramObj);
    paramArr.add(CommonConstant.LATEST);
    jsonObject.put(CommonConstant.PARAMS, paramArr);
    String postResult = client.postRetry(jsonObject.toJSONString());

    if (StringUtils.isBlank(postResult)) {
      log.error("getLastChildBlock fail, jsonObject is {}, result is null", jsonObject);
      return lastChildBlock;
    }

    JSONObject jsonResult = JSON.parseObject(postResult);
    if (jsonResult.containsKey(CommonConstant.ERROR)) {
      log.error("getLastChildBlock fail, jsonObject is {}, result is {}", jsonObject, postResult);
      return lastChildBlock;
    }

    String result = jsonResult.getString(CommonConstant.RESULT).replaceAll(CommonConstant.HEX_PREFIX_MATCHER, "");
    if (StringUtils.isNotEmpty(result)) {
      lastChildBlock = Long.parseLong(result, 16);
    } else {
      log.error("getLastChildBlock fail, jsonObject is {}, result is {}", jsonObject, postResult);
    }
    return lastChildBlock;
  }

  @Override
  public long getCurrentHeaderBlock() {
    long currentHeaderBlock = -1;
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.JSON_RPC, "2.0");
    jsonObject.put(CommonConstant.METHOD, CommonConstant.ETH_CALL);
    jsonObject.put("id", 1);
    JSONArray paramArr = new JSONArray();
    JSONObject paramObj = new JSONObject();
    paramObj.put("from", Util.ZERO_ADDRESS);
    paramObj.put("to", rootChainProxy);
    paramObj.put("data", Util.FUNC_CURRENT_HEADER_BLOCK);
    paramArr.add(paramObj);
    paramArr.add(CommonConstant.LATEST);
    jsonObject.put(CommonConstant.PARAMS, paramArr);
    String postResult = client.postRetry(jsonObject.toJSONString());

    if (StringUtils.isBlank(postResult)) {
      log.warn("getCurrentHeaderBlock fail, jsonObject is {}, result is {}", jsonObject, postResult);
      return currentHeaderBlock;
    }

    JSONObject jsonResult = JSON.parseObject(postResult);
    if (jsonResult.containsKey(CommonConstant.ERROR)) {
      log.warn("getCurrentHeaderBlock fail, jsonObject is {}, result is {}", jsonObject, postResult);
      return currentHeaderBlock;
    }

    String result = jsonResult.getString(CommonConstant.RESULT).replaceAll(CommonConstant.HEX_PREFIX_MATCHER, "");
    if (StringUtils.isNotEmpty(result)) {
      currentHeaderBlock = Long.parseLong(result, 16);
      currentHeaderBlock = currentHeaderBlock / 10000;
    }
    return currentHeaderBlock;
  }

  @Override
  public JSONObject getHeaderBlocks(long checkPointNumber) {
    JSONObject resultObj = new JSONObject();
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.JSON_RPC, "2.0");
    jsonObject.put(CommonConstant.METHOD, CommonConstant.ETH_CALL);
    jsonObject.put("id", 1);
    JSONArray paramArr = new JSONArray();
    JSONObject paramObj = new JSONObject();
    paramObj.put("from", "0x0000000000000000000000000000000000000000");
    paramObj.put("to", rootChainProxy);
    paramObj.put("data", Util.FUNC_HEADER_BLOCK + String.format("%064x", checkPointNumber * 10000L));
    paramArr.add(paramObj);
    paramArr.add(CommonConstant.LATEST);
    jsonObject.put(CommonConstant.PARAMS, paramArr);
    String postResult = client.postRetry(jsonObject.toJSONString());

    if (StringUtils.isBlank(postResult)) {
      return new JSONObject();
    }

    JSONObject jsonResult = JSON.parseObject(postResult);
    if (jsonResult.containsKey(CommonConstant.ERROR)) {
      return new JSONObject();
    }

    String result = jsonResult.getString(CommonConstant.RESULT);
    if (StringUtils.isNotEmpty(result)) {
      String start = result.substring(66, 66 + 64);
      String end = result.substring(66 + 64, 66 + 64 * 2);
      resultObj.put("start", Long.parseLong(start, 16));
      resultObj.put("end", Long.parseLong(end, 16));
    }
    return resultObj;
  }

  public long getLatestBlockNumber() {
    long blockNumber = 0L;
    JSONObject map = new JSONObject();
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_blockNumber");
    map.put("id", 1);
    String resp = client.postRetry(map.toJSONString());
    if (StringUtils.isBlank(resp)) {
      log.error("eth or bsc getBlockNumber blank");
      return 0L;
    }

    JSONObject result = JSON.parseObject(resp);
    if (result.containsKey(CommonConstant.RESULT)) {
      blockNumber = MathUtils.convertTo10Radix(result.getString(CommonConstant.RESULT));
    }
    return blockNumber;
  }

  public long getSolidityBlockNumber() {
    long blockNumber = 0L;
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.JSON_RPC, "2.0");
    jsonObject.put(CommonConstant.METHOD, "eth_getBlockByNumber");
    JSONArray jsonArray = new JSONArray();
    jsonArray.add("finalized");
    jsonArray.add(false);
    jsonObject.put(CommonConstant.PARAMS, jsonArray);
    jsonObject.put("id", "1");
    String resp = client.postRetry(jsonObject.toJSONString());
    if (StringUtils.isBlank(resp)) {
      log.error("eth or bsc getBlockNumber blank");
      return 0L;
    }

    JSONObject result = JSON.parseObject(resp);
    if (result.containsKey(CommonConstant.RESULT) && result.getJSONObject(CommonConstant.RESULT).containsKey("number")) {
      blockNumber = MathUtils.convertTo10Radix(result.getJSONObject(CommonConstant.RESULT).getString("number"));
    }
    return blockNumber;
  }

  private long getToBlockNumber(long fromBlockNum, boolean confirm, boolean addData) {
    long toBlockNum;
    long chainBlockNum = messageCenterConfigService.getBlockNumber(chainId, confirm);
    if (chainBlockNum > 0) {
      if (addData) {
        if (ChainTypeEnum.ETHEREUM.code == chainId) {
          chainBlockNum -= ETH_BLOCK_DELAY;
        } else if (ChainTypeEnum.BSC.code == chainId) {
          chainBlockNum -= BSC_BLOCK_DELAY;
        }
      }
      toBlockNum = Math.min(fromBlockNum + Util.BLOCK_RANGE, chainBlockNum);
      if (toBlockNum <= fromBlockNum) {
        return 0L;
      }
    } else {
      // didn't get the latest confirmed block number, return and retry later
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

    message.setFromChainId(ChainTypeEnum.fromCode(chainId).chainName);
    message.setToChainId("bttc");
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

  /**
   * Parse the log of the transaction receipt
   *
   * @param tx transaction info from the database
   * @return whether the parsing is successful
   */
  private boolean parseDestChainLog(Transactions tx, JSONObject txReceipt) {
    boolean result = false;
    try {
      for (int i = 0; i < txReceipt.getJSONArray("logs").size(); i++) {
        JSONObject logsObj = txReceipt.getJSONArray("logs").getJSONObject(i);
        JSONArray topicsArray = logsObj.getJSONArray(CommonConstant.TOPICS);
        String eventSign = topicsArray.getString(0).toLowerCase();
        if (eventSign.equalsIgnoreCase(Util.EXITED_ETHER_TOPICS) ||
            eventSign.equalsIgnoreCase(Util.EXITED_ERC20_TOPICS) ||
            eventSign.equalsIgnoreCase(Util.EXITED_MINTABLE_ERC20_TOPICS)) {
          // ExitedEther(exitor, amount)
          // ExitedERC20(address indexed withdrawer, address indexed rootToken, uint256 amount)
          // ExitedMintableERC20(address indexed withdrawer, address indexed rootToken, uint256 amount)
          String toAddress = "0x" + topicsArray.getString(1).substring(26);
          if (toAddress.equalsIgnoreCase(tx.getToAddress())) {
            String toAmount = MathUtils.convertTo10RadixInString(logsObj.getString("data"));
            tx.setToAmount(toAmount);
            String destToken = chainTokenAddress;
            if (!eventSign.equalsIgnoreCase(Util.EXITED_ETHER_TOPICS)) {
              destToken =
              "0x" + topicsArray.getString(2).substring(topicsArray.getString(2).length() - 40);
            }
            tx.setDestTokenId(destToken);
            tx.setToBlock(MathUtils.convertTo10Radix(txReceipt.getString(CommonConstant.BLOCK_NUMBER)));
            String ownerAddress = txReceipt.getString("from");
            tx.setDestTxOwner(ownerAddress);
            result = true;
          } else {
            log.error("chain {} parseDestChainLog error, toAddress is not same with the "
                    + "transaction table,tx hash is {}, parse toAddress is {}, toAddress in table is {}",
                chainId, tx.getDestTxid(), toAddress, tx.getToAddress());
          }
        }
      } //_for
    } catch (Exception e) {
      log.error("eth or bsc parseDestChainLog throw exception = [{}], source hash {} ",
          Throwables.getStackTraceAsString(e), tx.getSrcTxid());
    }

    return result;
  }

  public JSONObject getTransactionReceipt(String txid) {
    JSONArray params = new JSONArray();
    params.add(txid);
    JSONObject map = new JSONObject();
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_getTransactionReceipt");
    map.put("id", 1);
    map.put(CommonConstant.PARAMS, params);
    String resp = client.postRetry(map.toJSONString());
    if (StringUtils.isBlank(resp)) {
      log.error("getTransactionReceipt blank");
      return new JSONObject();
    }

    if (JSON.parseObject(resp).containsKey(CommonConstant.RESULT)) {
      if (MapUtils.isNotEmpty(JSON.parseObject(resp).getJSONObject(CommonConstant.RESULT))) {
        return JSON.parseObject(resp).getJSONObject(CommonConstant.RESULT);
      } else {
        return new JSONObject();
      }
    } else {
      log.warn("getTransactionReceipt error, the hash is {}, the result is {}", txid, resp);
      return new JSONObject();
    }
  }

  private JSONObject getLogsWithRetry(long fromBlock, long toBlock,
      List<String> contractAddressList, List<String> topicList) {
    JSONObject map = new JSONObject();
    JSONObject paramObj = new JSONObject();
    JSONArray params = new JSONArray();
    paramObj.put(CommonConstant.FROM_BLOCK, "0x" + Long.toHexString(fromBlock));
    paramObj.put(CommonConstant.TO_BLOCK, "0x" + Long.toHexString(toBlock));
    paramObj.put(CommonConstant.ADDRESS, contractAddressList);
    paramObj.put(CommonConstant.TOPICS, topicList);
    params.add(paramObj);
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_getLogs");
    map.put(CommonConstant.PARAMS, params);
    map.put("id", 1);

    String resp = client.postRetry(map.toJSONString());
    if (StringUtils.isNotBlank(resp)) {
      JSONObject logsObj = JSON.parseObject(resp);
      if (logsObj.containsKey(CommonConstant.RESULT)) {
        return logsObj;
      }
    }

    log.error("eth or bsc getLogsWithRetry error, result is {}", JSON.parseObject(resp));
    return new JSONObject();
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
    map.put(CommonConstant.METHOD, "eth_getLogs");
    map.put(CommonConstant.PARAMS, params);
    map.put("id", 1);

    String resp = client.postRetry(map.toJSONString());
    if (StringUtils.isNotBlank(resp)) {
      JSONObject logsObj = JSON.parseObject(resp);
      if (logsObj.containsKey(CommonConstant.RESULT)) {
        return logsObj;
      }
    }

    log.error("eth or bsc getMultiTopicsLogs error, the result is {}", JSON.parseObject(resp));
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

    String blockResp = client.postRetry(map.toJSONString());
    if (StringUtils.isBlank(blockResp)) {
      log.warn("eth_getBlockByNumber return null, the block number is {}", blockNumHex);
      return 0;
    }
    JSONObject respObj = new JSONObject();
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
      log.warn("abstractEthParseData get eth_getBlockByNumber error, the block num is {}, "
          + "return blockResp is {}", blockNumHex, blockResp);
    }
    log.warn("abstractEthParseData get eth_getBlockByNumber error, the block num is {}, "
        + "return value is {}", blockNumHex, respObj);
    return 0;
  }

  @Override
  public String getAccountLastNonce(String accountAddress, boolean normal) {
    String nonce;
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.JSON_RPC, "2.0");
    jsonObject.put(CommonConstant.METHOD, "eth_getTransactionCount"); // parity_nextNonce
    JSONArray jsonArray = new JSONArray();
    jsonArray.add(accountAddress);
    if (normal) {
      jsonArray.add("pending");
    } else {
      jsonArray.add(CommonConstant.LATEST);
    }
    jsonObject.put(CommonConstant.PARAMS, jsonArray);
    jsonObject.put("id", "1");

    String nonceResp = client.postRetry(jsonObject.toJSONString());
    if (StringUtils.isBlank(nonceResp)) {
      log.warn("eth_getTransactionCount return null, chain is {}, account is {}",
          chainId, accountAddress);
      return "";
    }
    try {
      JSONObject respObj = JSON.parseObject(nonceResp);
      nonce = respObj.getString(CommonConstant.RESULT);
      if (StringUtils.isNotBlank(nonce)) {
        nonce = MathUtils.convertTo10RadixInString(nonce);
      }
    } catch (Exception e) {
      log.warn(
          "abstractEthParseData getAccountLastNonce throw exception, chain is {}, account is {}, "
              + "result is {}, exception is {}",
          chainId, accountAddress, nonceResp, Throwables.getStackTraceAsString(e));
      throw e;
    }
    return nonce;
  }

  @Override
  public String getGasPrice() {
    String gasPrice = defaultGasPrice;
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.JSON_RPC, "2.0");
    jsonObject.put(CommonConstant.METHOD, "eth_gasPrice");
    jsonObject.put("id", 1);
    JSONArray paramArr = new JSONArray();
    jsonObject.put(CommonConstant.PARAMS, paramArr);

    String postResult = client.postRetry(jsonObject.toJSONString());

    if (StringUtils.isBlank(postResult) || "0x".equalsIgnoreCase(postResult)) {
      return gasPrice;
    }

    JSONObject jsonResult = JSON.parseObject(postResult);
    if (jsonResult.containsKey(CommonConstant.ERROR)) {
      return gasPrice;
    }

    String result = jsonResult.getString(CommonConstant.RESULT).replaceAll(CommonConstant.HEX_PREFIX_MATCHER, "");
    if (StringUtils.isNotEmpty(result)) {
      gasPrice = new BigInteger(result, 16).toString();
    }
    return gasPrice;
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
      return fromBlockNum - 1; //the parsed block number saved in the database is fromBlockNum - 1
    } else {
      log.info(taskName + ": there is no events in {} blocks, from {} to {}, confirm is {}, add is {}",
          Util.BLOCK_RANGE, fromBlockNum, toBlockNum, confirm, add);
      return fromBlockNum - 1 + 100;
    }
  }
}
