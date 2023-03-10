package org.bttc.relayer.schedule.strategy.impl.bttc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.base.Throwables;
import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.bttc.relayer.bean.dao.CheckPointInfo;
import org.bttc.relayer.bean.dao.MessageCenter;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.bean.enums.StatusEnum;
import org.bttc.relayer.client.SlackClient;
import org.bttc.relayer.config.AddressConfig;
import org.bttc.relayer.constant.CommonConstant;
import org.bttc.relayer.grpc.WalletClient;
import org.bttc.relayer.mapper.CheckPointInfoMapper;
import org.bttc.relayer.schedule.Util;
import org.bttc.relayer.schedule.strategy.BttcParseDataStrategy;
import org.bttc.relayer.service.MessageCenterConfigService;
import org.bttc.relayer.service.TransactionsService;
import org.bttc.relayer.utils.ContractUtil;
import org.bttc.relayer.utils.HttpClientUtil;
import org.bttc.relayer.utils.MathUtils;
import org.bttc.relayer.utils.Sha256Hash;
import org.bttc.relayer.utils.SignUtils;
import org.bttc.relayer.utils.TronUtils;
import org.springframework.stereotype.Service;
import org.tron.api.GrpcAPI.Return;
import org.tron.api.GrpcAPI.TransactionExtention;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.protos.Protocol.Transaction;

/**
 * @Author: tron
 * @Date: 2022/2/21
 */
@Service("BttcTronParseData")
@Slf4j
public class TronParseData implements BttcParseDataStrategy {

  private static final int CHAIN_ID = ChainTypeEnum.TRON.code;
  //block count generated in 30 minutes
  private static final long BLOCK_DELAY = 600;

  private AddressConfig addressConfig;
  private TransactionsService transactionsService;
  private MessageCenterConfigService messageCenterConfigService;
  private CheckPointInfoMapper checkPointInfoMapper;
  private WalletClient walletClient;

  @Resource
  private SlackClient slackClient;
  private SecureRandom rand = new SecureRandom();
  private static final String STATUS_SUCCESS = "SUCCESS";

  private String tronGetBlockByNumUrl;
  private String tronGetBlockByNumUrlSolidity;
  private String tronGetTransactionByIdUrl;
  private String tronGetTransactionByIdUrlSolidity;
  private String tronGetTransactionInfoByIdUrl;
  private String tronGetTransactionInfoByIdUrlSolidity;
  private String tronGetNowBlockUrl;
  private String tronGetSolidityNowBlockUrl;
  private String tronAddressEventUrl;
  private String eventListUrl;
  private String tronGridBaseUrl;
  private String triggerConstantContractUrl;

  private String apiKeyName;
  private String apiKey;

  private String rootChainManagerProxy;
  private String rootChainProxy;
  private String etherPredicateProxy;
  private String erc20PredicateProxy;
  private String mintableERC20PredicateProxy;

  private String chainTokenAddress;
  private static final String DEFAULT_TRON_GAS_PRICE = "420";
  private BigInteger defaultGasLimit;

  private String relayerAddress;
  private ECKey ecKey;

  public TronParseData(AddressConfig addressConfig,
      TransactionsService transactionsService,
      CheckPointInfoMapper checkPointInfoMapper,
      MessageCenterConfigService messageCenterConfigService,
      WalletClient walletClient) {
    this.addressConfig = addressConfig;
    this.transactionsService = transactionsService;
    this.checkPointInfoMapper = checkPointInfoMapper;
    this.messageCenterConfigService = messageCenterConfigService;
    this.walletClient = walletClient;
  }

  @PostConstruct
  public void init() {
    this.rootChainManagerProxy = addressConfig.getTronRootChainManagerProxy();
    this.chainTokenAddress = addressConfig.getTronChainTokenAddress();
    this.rootChainProxy = addressConfig.getTronRootChainProxy();
    this.etherPredicateProxy = addressConfig.getTronEtherPredicateProxy();
    this.erc20PredicateProxy = addressConfig.getTronERC20PredicateProxy();
    this.mintableERC20PredicateProxy = addressConfig.getTronMintableERC20PredicateProxy();

    tronGetBlockByNumUrl = addressConfig.getTronGetBlockByNumUrl();
    tronGetBlockByNumUrlSolidity = addressConfig.getTronGetBlockByNumUrlSolidity();
    tronGetTransactionByIdUrl = addressConfig.getTronGetTransactionByIdUrl();
    tronGetTransactionByIdUrlSolidity = addressConfig.getTronGetTransactionByIdUrlSolidity();
    tronGetTransactionInfoByIdUrl = addressConfig.getTronGetTransactionInfoByIdUrl();
    tronGetTransactionInfoByIdUrlSolidity = addressConfig.getTronGetTransactionInfoByIdUrlSolidity();
    tronGetNowBlockUrl = addressConfig.getTronGetNowBlockUrl();
    tronGetSolidityNowBlockUrl = addressConfig.getTronGetNowBlockUrlSolidity();
    apiKeyName = addressConfig.getApiKeyName();
    apiKey = addressConfig.getApiKey();

    tronAddressEventUrl = addressConfig.getTronAddressEventUrl();
    eventListUrl = tronAddressEventUrl + "/#1/events?event_name=#2&limit=50&only_confirmed=true";
    tronGridBaseUrl = addressConfig.getTronBaseUrl();

    triggerConstantContractUrl = addressConfig.getTronBaseUrl() + "triggerconstantcontract";

    defaultGasLimit = new BigInteger(CommonConstant.DEFAULT_TRON_WITHDRAW_GAS_LIMIT);

    byte[] privateKey = ByteArray.fromHexString(addressConfig.getRelayerKey());
    ecKey = ECKey.fromPrivate(privateKey);
    relayerAddress = TronUtils.encode58Check(ecKey.getAddress());
  }

  @Override
  @SuppressWarnings("squid:S3776")
  public long parseMainChainCheckpoint(long fromBlockNum, boolean confirm)  {
    long toBlockNum = getToBlockNumber(fromBlockNum, confirm, false);
    if (toBlockNum == 0L) {
      return 0L;
    }
    long eventBlockNum = fromBlockNum - 1;
    String reqUrl = getReqUrlByFromBlockAndToBlock(
        rootChainProxy, Util.NEW_HEADER_BLOCK, fromBlockNum, toBlockNum, confirm);
    if (StringUtils.isBlank(reqUrl)) {
      // get url fail, return 0 and retry later
      return 0L;
    }

    boolean parseError = false;
    List<CheckPointInfo> checkPointInfoList = new ArrayList<>();
    // Keep parsing until no events of the contract can be obtained
    while (StringUtils.isNotBlank(reqUrl)) {
      @SuppressWarnings("squid:S2259")
      JSONObject resp = getResponseObject(reqUrl);
      if (ObjectUtils.isEmpty(resp)) {
        parseError = true;
        break;
      }
      @SuppressWarnings("squid:S2259")
      JSONArray txArr = resp.getJSONArray("data");
      if (txArr.isEmpty()) {
        break;
      }

      for (int i = 0; i < txArr.size(); i++) {
        JSONObject data = txArr.getJSONObject(i);
        eventBlockNum = Math.max(eventBlockNum, data.getLongValue(CommonConstant.TRON_BLOCK_NUMBER));
        // CheckPointInfo
        int resultValue = parseCheckPointLog(data, checkPointInfoList, rootChainProxy, confirm);
        if (resultValue != CommonConstant.RETURN_SUCCESS) {
          parseError = true;
          break;
        }
      } //_for

      // If the number of events in the query range exceeds 50, you need to reconstruct the query url
      String nextReqUrl =
          resp.getJSONObject(CommonConstant.TRON_META).containsKey(CommonConstant.TRON_LINK) ?
              resp.getJSONObject(CommonConstant.TRON_META)
              .getJSONObject(CommonConstant.TRON_LINK).getString("next") : "";
      if (StringUtils.isNotBlank(nextReqUrl) && !parseError) {
        reqUrl = nextReqUrl;
      } else {
        break;
      }
    } //_while

    // If there is no error in the whole parsing process, store the parsing result in the database
    if (!parseError) {
      if (!checkPointInfoList.isEmpty()) {
        int resultValue = saveCheckPointInfo(checkPointInfoList, confirm);
        if (resultValue != CommonConstant.RETURN_SUCCESS) {
          return 0L;
        }
      }
      return getParseToBlock( eventBlockNum,  fromBlockNum,  toBlockNum,
          confirm,  false, checkPointInfoList.size(), "main chain checkpoint");
    }
    // parse error,return 0 and retry later
    return 0L;
  }

  private int parseCheckPointLog(JSONObject data, List<CheckPointInfo> checkPointInfoList,
      String contractAddress, boolean confirm) {
    if (!rootChainProxy.equalsIgnoreCase(contractAddress)) {
      log.error("get checkPointNum fail, wrong contractAddress is {}, the data is {}",
          contractAddress, data);
      return CommonConstant.RETURN_FAIL;
    }
    CheckPointInfo checkPointInfo = new CheckPointInfo();
    String txid = data.getString(CommonConstant.TRON_TRANSACTION_ID);
    checkPointInfo.setTxId(txid);
    JSONObject result = data.getJSONObject(CommonConstant.TRON_RESULT);
    String checkPointNum = result.getString("headerBlockId");
    if (StringUtils.isNotBlank(checkPointNum) && checkPointNum.length() > 4) {
      checkPointNum = checkPointNum.substring(0, checkPointNum.length() - 4);
    } else {
      log.error("get checkPointNum fail, the txid is {}, the result is {}", txid, result);
      return CommonConstant.RETURN_FAIL;
    }
    long startBlock = result.getLongValue("start");
    long endBlock = result.getLongValue("end");
    checkPointInfo.setCheckPointNum(Long.parseLong(checkPointNum));
    checkPointInfo.setStartBlock(startBlock);
    checkPointInfo.setEndBlock(endBlock);
    int status = 0;
    if (confirm) {
      status = 1;
    }
    checkPointInfo.setConfirm(status);
    checkPointInfo.setBlockNumber(data.getLongValue(CommonConstant.TRON_BLOCK_NUMBER));
    checkPointInfo.setChainId(ChainTypeEnum.TRON.code);
    checkPointInfo.setResult(CommonConstant.SUCCESS);
    checkPointInfo.setTimeStamp(Util.convertTimeStampToDate(data.getLongValue("block_timestamp")));
    checkPointInfoList.add(checkPointInfo);
    log.info("main chain {}: checkpoint message is about to save into db, txid: {}, confirm: {}",
        CHAIN_ID, checkPointInfo.getTxId(), confirm);
    return CommonConstant.RETURN_SUCCESS;
  }

  private int saveCheckPointInfo(List<CheckPointInfo> checkPointInfoList, boolean confirm) {
    if (checkPointInfoList.isEmpty()) {
      return CommonConstant.RETURN_SUCCESS;
    }
    if (confirm) {
      int num = checkPointInfoMapper.insertConfirmMsg(checkPointInfoList);
      if (num < checkPointInfoList.size()) {
        log.error("[ALERT]_Tron checkpoint message insert into db error, return num: {}", num);
        return CommonConstant.RETURN_FAIL;
      }
    } else {
      checkPointInfoMapper.insertUnConfirmMsg(checkPointInfoList);
    }
    return CommonConstant.RETURN_SUCCESS;
  }

  @Override
  public int parseMainChainSubmitWithdraw(Transactions tx, String payload,
      boolean normal) {
    String format = "%064x";
    payload = payload.substring(2);

    // assemble parameters
    // function signature +
    // (data start position of dynamic parameters, excluding function signature) data start position +
    // dynamic parameter data length +
    // Dynamic parameter actual data +
    // data completion
    int dataStartOffset = CommonConstant.ABI_HEAD_OR_TAIL_LENGTH_IN_BYTE;
    String dataStartOffsetStr = String.format(format, dataStartOffset);
    int dataLengthOffset = payload.length() / 2;
    String dataLengthOffsetStr = String.format(format, dataLengthOffset);
    // Add "0" at the end of less than 32 bits
    // fill '0' at the end until the length is the multiple of 32
    int appendLength = CommonConstant.DATA_APPEND_LENGTH -
        (payload.length() % CommonConstant.DATA_APPEND_LENGTH);
    StringBuilder appendString = new StringBuilder();
    for (int i = 0; i < appendLength; ++i) {
      appendString.append("0");
    }

    String dataStr = SignUtils.getMethodSign("exit(bytes)")
        + dataStartOffsetStr
        + dataLengthOffsetStr
        + payload
        + appendString.toString();

    TransactionExtention transactionExtention = walletClient.triggerSmartContract(
        TronUtils.decodeFromBase58Check(rootChainManagerProxy),
        TronUtils.decodeFromBase58Check(relayerAddress),
        ByteArray.fromHexString(dataStr)
    );

    // set fee limit
    BigInteger feeLimit = defaultGasLimit.multiply(new BigInteger(getGasPrice()));
    TransactionExtention.Builder texBuilder = TransactionExtention.newBuilder();
    Transaction.Builder transBuilder = Transaction.newBuilder();
    Transaction.raw.Builder rawBuilder = transactionExtention.getTransaction().getRawData()
        .toBuilder();
    rawBuilder.setFeeLimit(feeLimit.longValue());
    transBuilder.setRawData(rawBuilder);
    texBuilder.setTransaction(transBuilder);
    transactionExtention = texBuilder.build();

    Transaction transaction = transactionExtention.getTransaction();
    byte[] rawData = transaction.getRawData().toByteArray();
    byte[] hash = Sha256Hash.hash(rawData);
    byte[] sign = ecKey.sign(hash).toByteArray();
    Transaction transactionSign = transaction.toBuilder().addSignature(ByteString.copyFrom(sign))
        .build();
    Return ret = walletClient.broadcastTransaction(transactionSign);
    if (!ret.getResult()) {
      String msg = String.format("Tron broadcastTransaction failed, code = %d, message = %s",
          ret.getCode().getNumber(), ret.getMessage().toStringUtf8());
      log.error(msg);
      slackClient.sendTextMessage(msg);
      return CommonConstant.RETURN_FAIL;
    }

    String transactionHash = ByteArray.toHexString(hash);
    log.info("Submit withdraw for tx {} main chain {} tx hash {} normal = {}",
        tx.getSrcTxid(), CHAIN_ID, transactionHash, normal);

    // Change the transaction status and store it in the database
    try {
      int num = transactionsService.parseMainChainSubmitWithdrawMessage(
          tx.getSrcTxid(), transactionHash, "2");
      if (num < 1) {
        String msg = String.format("Main chain %d withdraw message insert into db error, return num: %d",
            CHAIN_ID, num);
        log.error(msg);
        slackClient.sendTextMessage(msg);
      }
    } catch (Exception e) {
      log.error("[ALERT]_Save main chain {} submit srcTxId {} withdraw throw exception {}",
          CHAIN_ID, tx.getSrcTxid(), Throwables.getStackTraceAsString(e));
      String msg = String.format("Save main chain %d submit srcTxId %s withdraw failed",
          CHAIN_ID, tx.getSrcTxid());
      slackClient.sendTextMessage(msg);
    }

    return CommonConstant.RETURN_SUCCESS;
  }

  @Override
  @SuppressWarnings("squid:S3776")
  public long parseMainChainWithdraw(long fromBlockNum, boolean chainToken, boolean confirm, boolean add)
   {
    long toBlockNum = getToBlockNumber(fromBlockNum, confirm, add);
    long eventBlockNum = fromBlockNum - 1;
    if (toBlockNum == 0L) {
      return 0L;
    }

    Map<String, String> eventMap = new HashMap<>();
    if (chainToken) {
      eventMap.put(etherPredicateProxy, Util.EXITED_ETHER);
    } else {
      eventMap.put(erc20PredicateProxy, Util.EXITED_ERC20);
      eventMap.put(mintableERC20PredicateProxy, Util.EXITED_MINTABLE_ERC20);
    }
    boolean parseError = false;
    List<MessageCenter> messageList = new ArrayList<>();
    for (Map.Entry<String, String> eventMapEntry : eventMap.entrySet()) {
      String contractAddress = eventMapEntry.getKey();
      String eventName = eventMapEntry.getValue();
      String reqUrl = getReqUrlByFromBlockAndToBlock(
          contractAddress, eventName, fromBlockNum, toBlockNum, confirm);
      if (StringUtils.isBlank(reqUrl)) {
        // get url error, return 0 and retry later
        parseError = true;
        break;
      }
      // Keep parsing until no events of the contract can be obtained
      while (StringUtils.isNotBlank(reqUrl)) {
        JSONObject resp = getResponseObject(reqUrl);
        if (ObjectUtils.isEmpty(resp)) {
          parseError = true;
          break;
        }
        @SuppressWarnings("squid:S2259")
        JSONArray txArr = resp.getJSONArray("data");
        for (int i = 0; i < txArr.size(); i++) {
          JSONObject data = txArr.getJSONObject(i);
          if (add) {
            // if add, only process the missed transactions
            String hash = data.getString(CommonConstant.TRON_TRANSACTION_ID);
            QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("dest_txid", hash)
                .eq("dest_chain_id", ChainTypeEnum.TRON.code);
            Transactions tx = transactionsService.getBaseMapper().selectOne(queryWrapper);
            if (ObjectUtils.isNotEmpty(tx)) {
              continue;
            } else {
              log.info("tron withdraw add tx: {}", hash);
            }
          }
          int resultValue = parseMainChainWithdrawLog(data, messageList, contractAddress, confirm);
          if (resultValue != CommonConstant.RETURN_SUCCESS) {
            parseError = true;
            break;
          }
        } //_for

        // If the number of events in the query range exceeds 50, you need to reconstruct the query url
        String nextReqUrl =
            resp.getJSONObject(CommonConstant.TRON_META).containsKey(CommonConstant.TRON_LINK) ?
                resp.getJSONObject(CommonConstant.TRON_META)
                .getJSONObject(CommonConstant.TRON_LINK).getString("next") : "";
        reqUrl = nextReqUrl;
      } //_while
    } //_for

    // If there is no error in the whole parsing process, store the parsing result in the database
    if (!parseError) {
      for (MessageCenter messageCenter : messageList) {
        eventBlockNum = Math.max(eventBlockNum, messageCenter.getBlockNumber());
      }

      return getParseToBlock( eventBlockNum,  fromBlockNum,  toBlockNum,
          confirm,  add, messageList.size(), "main chain withdraw");
    }
    return 0L;
  }

  private int parseMainChainWithdrawLog(
      JSONObject data, List<MessageCenter> messageList, String contractAddress, boolean confirm) {
    String eventType = data.getString(CommonConstant.TRON_EVENT_NAME);
    MessageCenter message = getBasicMessageFromEvent(
        data, contractAddress, eventType, confirm);
    String toAddress = "";
    BigInteger amount = BigInteger.ZERO;
    String tokenId = "";
    JSONObject result = data.getJSONObject(CommonConstant.TRON_RESULT);
    if (Util.EXITED_ETHER.equalsIgnoreCase(eventType)) {
      // ExitedEther(index_topic_1 exitor, uint256 amount)
      toAddress = result.getString("0");
      amount = new BigInteger(result.getString("1"));
      tokenId = chainTokenAddress;
    } else if (Util.EXITED_ERC20.equalsIgnoreCase(eventType) || Util.EXITED_MINTABLE_ERC20
        .equalsIgnoreCase(eventType)) {
      JSONObject txReceipt = getTransactionReceipt(message.getTxId(), false);
      JSONObject exitedERC20Topics;
      JSONArray logArray = txReceipt.getJSONArray("log");
      if (CollectionUtils.isNotEmpty(logArray)) {
        exitedERC20Topics = (JSONObject) (logArray.get(message.getEventIndex()));
      } else {
        log.warn(" tron get txReceipt error, the txid is {}, the txReceipt is {}",
            message.getTxId(),
            txReceipt);
        return CommonConstant.RETURN_FAIL;
      }
      JSONArray topicsArray = exitedERC20Topics.getJSONArray(CommonConstant.TOPICS); //1-to;2-token
      toAddress = topicsArray.getString(1).substring(topicsArray.getString(1).length() - 40);
      tokenId = TronUtils.convertEthAddressToTronAddress(
          topicsArray.getString(2).substring(topicsArray.getString(2).length() - 40));
      // parse Data: amount(uint256)
      amount = new BigInteger(MathUtils.convertTo10RadixInString(
          exitedERC20Topics.getString("data")));
    }
    String ownerAddress = getTxOwner(message.getTxId());
    if (StringUtils.isBlank(ownerAddress)) {
      log.warn(" tron get tx owner fail, the txid is {}", message.getTxId());
      return CommonConstant.RETURN_FAIL;
    }

    message.setFromAddress(ownerAddress);
    message.setToAddress(toAddress);
    message.setAmount(amount.toString());
    message.setTokenId(tokenId);

    String sideChainHash = "";
    try {
      sideChainHash = transactionsService.getSideChainWithdrawTransactionHash(
          message.getTxId(), CHAIN_ID);
    } catch (Exception e) {
      log.error("[ALERT]_Get side chain hash exception. mainChainHash = [{}], Exception = [{}]",
          message.getTxId(), Throwables.getStackTraceAsString(e));
      return CommonConstant.RETURN_FAIL;
    }
    if (StringUtils.isBlank(sideChainHash)) {
      log.error("[ALERT]_Get side chain hash error. mainChainHash = [{}]", message.getTxId());
      return CommonConstant.RETURN_FAIL;
    }
    int num = transactionsService.parseMainChainWithdrawMessage(message, sideChainHash, CHAIN_ID);
    if (confirm && (num <= 0)) {
      String msg = String.format(
          "Main chain %d withdraw transaction insert into db error, txid: %s, return num: %d",
          CHAIN_ID, sideChainHash, num);
      log.error(msg);
      slackClient.sendTextMessage(msg);
    } else if (1 == num) {
      log.info("Tron withdraw message is about to save into db, txid: {}, confirm: {}",
          message.getTxId(), confirm);
    }
    messageList.add(message);

    return CommonConstant.RETURN_SUCCESS;
  }

  @Override
  @SuppressWarnings("squid:S3776")
  public int parseToChainData(Transactions tx, boolean confirm) {
    try {
      int status = tx.getTStatus();
      JSONObject txReceipt = getTransactionReceipt(tx.getDestTxid(), confirm);
      if (MapUtils.isNotEmpty(txReceipt)) {
        String contractAddress = txReceipt.getString(CommonConstant.TRON_CONTRACT_ADDRESS);
        contractAddress = ContractUtil.getAddressFromHex(contractAddress);
        if (rootChainManagerProxy.equalsIgnoreCase(contractAddress)) {
          if (STATUS_SUCCESS.equalsIgnoreCase(
              txReceipt.getJSONObject("receipt").getString(CommonConstant.TRON_RESULT))) {
            if (parseDestChainLog(tx, txReceipt)) {
              if (status != StatusEnum.DEST_CHAIN_ON_CHAIN.getValue()) {
                tx.setDestContractRet(CommonConstant.SUCCESS);
                tx.setTStatus(StatusEnum.DEST_CHAIN_ON_CHAIN.getValue());
                tx.setUpdateTime(new Date());
                log.info(
                    "tron tx parseToChainData success, source hash: {} dest hash: {} status switch to {}",
                    tx.getSrcTxid(), tx.getDestTxid(), tx.getTStatus());
              }
            } else {
              tx.setDestContractRet(CommonConstant.FAIL);
              tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLE_FAILED.getValue());
              tx.setUpdateTime(new Date());
              String msg = String.format("Main chain %d parseToChainData failed, source hash: %s, dest hash: %s, status switch to %d",
                  CHAIN_ID, tx.getSrcTxid(), tx.getDestTxid(), tx.getTStatus());
              log.error(msg);
              slackClient.sendTextMessage(msg);
            }
          } else {
            //Determine whether the cause of the error is because someone else has already withdrawn money,
            // if so, go to the next step, otherwise report an error and try again
            boolean alreadyProcessed = checkAlreadyProcessed(txReceipt);
            if (alreadyProcessed) {
              tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLED.getValue());
              tx.setUpdateTime(new Date());
              String msg = String.format("Tx EXIT_ALREADY_PROCESSED, chain is %d, source hash is %s",
                  CHAIN_ID, tx.getSrcTxid());
              log.warn(msg);
              slackClient.sendTextMessage(msg);
            } else {
              tx.setDestContractRet(CommonConstant.FAIL);
              tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLE_FAILED.getValue());
              tx.setUpdateTime(new Date());
              String msg = String.format(
                  "Main chain %d parseToChainData failed, tx failed, source hash: %s, dest hash: %s, status switch to %d",
                  CHAIN_ID, tx.getSrcTxid(), tx.getDestTxid(), tx.getTStatus());
              log.error(msg);
              slackClient.sendTextMessage(msg);
            }
          }
        } else {
          tx.setToBlock(Long.parseLong(txReceipt.getString("blockNumber")));
          tx.setTStatus(StatusEnum.DEST_CHAIN_HASH_ERROR.getValue());
          tx.setUpdateTime(new Date());
          String msg = String.format("Main chain %d parseToChainData failed, "
                  + "the contractAddress is not rootChainManagerProxy,"
                  + "source hash: %s, dest hash: %s, status switch to %d",
              CHAIN_ID, tx.getSrcTxid(), tx.getDestTxid(), tx.getTStatus());
          log.error(msg);
          slackClient.sendTextMessage(msg);
        }
        setDestTimestamp(tx, txReceipt);
      } else {
        // The transaction information cannot be obtained, and the status turn to DEST_CHAIN_HANDLING
        tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLING.getValue());
      }
    } catch (Exception e) {
      log.error("tron parseToChainData throw exception = [{}]",
          Throwables.getStackTraceAsString(e));
      // The transaction information cannot be obtained, and the status turn to DEST_CHAIN_HANDLING
      tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLING.getValue());
    }

    return tx.getTStatus();
  }

  public void setDestTimestamp(Transactions tx, JSONObject txReceipt) {
    try {
      String timestamp = txReceipt.getString("blockTimeStamp");
      long blockTimestamp = Long.parseLong(timestamp);

      if (blockTimestamp != 0L) {
        tx.setDestTimestamp(new Date(blockTimestamp));
      } else {
        log.warn("tron tx {} get dest timestamp failed, dest hash: {}",
            tx.getSrcTxid(), tx.getDestTxid());
        tx.setDestTimestamp(new Date());
      }
    } catch (Exception e) {
      log.warn("tron tx {} get dest timestamp failed, dest hash: {}",
          tx.getSrcTxid(), tx.getDestTxid());
      tx.setDestTimestamp(new Date());
    }
  }

  @Override
  public String getTransactionInput(String hash) {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.TRON_VALUE, hash);
    String postResult = HttpClientUtil.doPostJsonWithApiKeyRetry(
        tronGetTransactionByIdUrl,
        jsonObject.toJSONString(),
        apiKeyName,
        apiKey,
        CommonConstant.CONTROLLER_RETRY_TIMES);
    if (StringUtils.isBlank(postResult)) {
      return null;
    }
    JSONObject transactionObject = JSON.parseObject(postResult);
    return transactionObject.getJSONObject(CommonConstant.TRON_RAW_DATA).getJSONArray("contract").getJSONObject(0)
        .getJSONObject(CommonConstant.TRON_PARAMETER).getJSONObject(CommonConstant.TRON_VALUE).getString("data");
  }

  @Override
  @SuppressWarnings("squid:S3776")
  public long[] getCheckPointBlockNumber(long fromBlock) {
    // blockNumber[0]: main chain block number, blockNumber[1]: side chain block number
    long[] blockNumber = {0L, 0L};
    boolean resultFound = false;
    String requestUrl = eventListUrl.replace("#1", rootChainProxy);
    // Keep parsing until no new events of the contract can be obtained
    while (StringUtils.isNotBlank(requestUrl)) {
      String resultStr = HttpClientUtil.getByApiKey(requestUrl,
          apiKeyName,
          apiKey);
      if (StringUtils.isBlank(resultStr)) {
        log.error("tron getCheckPointBlockNumber error, resultStr is blank!");
        break;
      }
      JSONObject result = JSON.parseObject(resultStr);
      for (int i = 0; i < result.getJSONArray("data").size(); i++) {
        JSONObject logsObj = result.getJSONArray("data").getJSONObject(i);
        long blockNumberFromLog = Long.parseLong(logsObj.getString(CommonConstant.TRON_BLOCK_NUMBER));
        if (blockNumberFromLog < fromBlock) {
          // If the currently parsed block number is already less than the last parsed block number, terminate and return
          resultFound = true;
          break;
        }
        // Record the maximum block number of the main chain analyzed in this round
        if (blockNumber[0] == 0) {
          blockNumber[0] = blockNumberFromLog;
        }
        String eventName = logsObj.getString(CommonConstant.TRON_EVENT_NAME);
        if (Util.NEW_HEADER_BLOCK.equalsIgnoreCase(eventName)) {
          // get the NewHeaderBlock event, get the maximum block number of checkpoint, record and return
          long end = Long.parseLong(logsObj.getJSONObject(CommonConstant.TRON_RESULT).getString("end"));
          blockNumber[1] = end;
          resultFound = true;
          break;
        }
      } //_for
      if (resultFound) {
        break;
      }
      String nextReqUrl =
          result.getJSONObject(CommonConstant.TRON_META).containsKey(CommonConstant.TRON_LINK) ?
              result.getJSONObject(CommonConstant.TRON_META)
              .getJSONObject(CommonConstant.TRON_LINK).getString("next") : "";
      requestUrl = nextReqUrl;
    } //_while
    return blockNumber;
  }

  @Override
  public long getBlockNumber(boolean confirm) {
    if (confirm) {
      return getSolidityBlockNumber();
    } else {
      return getLatestBlockNumber();
    }
  }

  @Override
  public long getLastChildBlock() {
    long lastChildBlock = -1;
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.TRON_OWNER_ADDRESS, addressConfig.getTronRootChainProxy());
    jsonObject.put(CommonConstant.TRON_CONTRACT_ADDRESS, addressConfig.getTronRootChainProxy());
    jsonObject.put(CommonConstant.TRON_FUNCTION_SELECTOR, "getLastChildBlock()");
    jsonObject.put(CommonConstant.TRON_VISIBLE, true);
    String resultStr = HttpClientUtil.doPostJsonWithApiKeyRetry(triggerConstantContractUrl, jsonObject.toJSONString(), apiKeyName, apiKey,
        CommonConstant.RETRY_TIMES);
    if (StringUtils.isBlank(resultStr)) {
      log.error("getLastChildBlock null, jsonObject is {}", jsonObject);
      return lastChildBlock;
    }
    JSONObject result = JSON.parseObject(resultStr);
    String lastChildBlockStr = result.getJSONArray(CommonConstant.TRON_CONSTANT_RESULT).getString(0);
    if (StringUtils.isBlank(lastChildBlockStr)) {
      log.error("getLastChildBlock fail, jsonObject is {}, result is {}", jsonObject, result);
      return lastChildBlock;
    }
    if (lastChildBlockStr.length() > 64) {
      lastChildBlockStr = lastChildBlockStr.substring(0, 64);
    }
    lastChildBlock = Long.parseLong(lastChildBlockStr, 16);
    return lastChildBlock;
  }

  @Override
  public long getCurrentHeaderBlock() {
    long currentHeaderBlock = -1;
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.TRON_OWNER_ADDRESS, addressConfig.getTronRootChainProxy());
    jsonObject.put(CommonConstant.TRON_CONTRACT_ADDRESS, addressConfig.getTronRootChainProxy());
    jsonObject.put(CommonConstant.TRON_FUNCTION_SELECTOR, "currentHeaderBlock()");
    jsonObject.put(CommonConstant.TRON_VISIBLE, true);
    String resultStr = HttpClientUtil.doPostJsonWithApiKeyRetry(triggerConstantContractUrl, jsonObject.toJSONString(), apiKeyName, apiKey,
        CommonConstant.RETRY_TIMES);
    if (StringUtils.isBlank(resultStr)) {
      return currentHeaderBlock;
    }
    JSONObject result = JSON.parseObject(resultStr);
    String lastChildBlockStr = result.getJSONArray(CommonConstant.TRON_CONSTANT_RESULT).getString(0);
    if (lastChildBlockStr.length() > 64) {
      lastChildBlockStr = lastChildBlockStr.substring(0, 64);
    }
    currentHeaderBlock = Long.parseLong(lastChildBlockStr, 16);
    currentHeaderBlock = currentHeaderBlock / 10000;
    return currentHeaderBlock;
  }

  @Override
  public JSONObject getHeaderBlocks(long checkPointNumber) {
    JSONObject resultObj = new JSONObject();
    String dataStr = String.format("%064x", checkPointNumber * 10000L);
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.TRON_OWNER_ADDRESS, addressConfig.getTronRootChainProxy());
    jsonObject.put(CommonConstant.TRON_CONTRACT_ADDRESS, addressConfig.getTronRootChainProxy());
    jsonObject.put(CommonConstant.TRON_FUNCTION_SELECTOR, "headerBlocks(uint256)");
    jsonObject.put(CommonConstant.TRON_PARAMETER, dataStr);
    jsonObject.put(CommonConstant.TRON_VISIBLE, true);
    String resultStr = HttpClientUtil.doPostJsonWithApiKeyRetry(triggerConstantContractUrl, jsonObject.toJSONString(), apiKeyName, apiKey,
        CommonConstant.RETRY_TIMES);
    if (StringUtils.isBlank(resultStr)) {
      return new JSONObject();
    }
    JSONObject result = JSON.parseObject(resultStr);
    String constantResult = result.getJSONArray(CommonConstant.TRON_CONSTANT_RESULT).getString(0);
    if (StringUtils.isBlank(constantResult)) {
      return new JSONObject();
    }
    String start = constantResult.substring(64, 64 * 2);
    String end = constantResult.substring(64 * 2, 64 * 3);
    resultObj.put("start", Long.parseLong(start, 16));
    resultObj.put("end", Long.parseLong(end, 16));
    return resultObj;
  }

  public long getLatestBlockNumber() {
    long blockNumber;

    String getResult = HttpClientUtil.getByApiKey(
        tronGetNowBlockUrl,
        apiKeyName,
        apiKey);
    if (StringUtils.isBlank(getResult)) {
      return 0L;
    }
    JSONObject result = JSON.parseObject(getResult);
    try {
      blockNumber = Long.valueOf(
          result.getJSONObject(CommonConstant.TRON_BLOCK_HEADER).getJSONObject(CommonConstant.TRON_RAW_DATA).getString("number"));
    } catch (NullPointerException e) {
      log.warn("get the block num fail, the result is {}", getResult);
      return 0;
    }
    return blockNumber;
  }

  /**
   * get the latest confirmed block number
   */
  public long getSolidityBlockNumber() {
    long blockNumber;

    String getResult = HttpClientUtil.getByApiKey(
        tronGetSolidityNowBlockUrl,
        apiKeyName,
        apiKey);
    if (StringUtils.isBlank(getResult)) {
      return 0L;
    }
    JSONObject result = JSON.parseObject(getResult);
    try {
      blockNumber = Long.valueOf(
          result.getJSONObject(CommonConstant.TRON_BLOCK_HEADER).getJSONObject(CommonConstant.TRON_RAW_DATA).getString("number"));
    } catch (NullPointerException e) {
      log.warn("get the solidity block num fail, the result is {}", getResult);
      return 0;
    }
    return blockNumber;
  }

  /**
   * Parse the log of the transaction receipt
   *
   * @param tx transaction info from the database
   * @return whether the parsing is successful
   */
  @SuppressWarnings("squid:S3776")
  private boolean parseDestChainLog(Transactions tx, JSONObject txReceipt) {
    boolean result = false;
    try {
      for (int i = 0; i < txReceipt.getJSONArray("log").size(); i++) {
        JSONObject logsObj = txReceipt.getJSONArray("log").getJSONObject(i);
        JSONArray topicsArray = logsObj.getJSONArray(CommonConstant.TOPICS);
        String eventSign = "0x" + topicsArray.getString(0).toLowerCase();
        if (eventSign.equalsIgnoreCase(Util.EXITED_ETHER_TOPICS) ||
            eventSign.equalsIgnoreCase(Util.EXITED_ERC20_TOPICS) ||
            eventSign.equalsIgnoreCase(Util.EXITED_MINTABLE_ERC20_TOPICS)) {
          String toAddress = "0x" + topicsArray.getString(1).substring(24);
          toAddress = ContractUtil.getAddressFromEthHex(toAddress);
          if (toAddress.equalsIgnoreCase(tx.getToAddress())) {
            String toAmount = MathUtils.convertTo10RadixInString(logsObj.getString("data"));
            tx.setToAmount(toAmount);
            String tokenId = addressConfig.getTronChainTokenAddress();
            if (!eventSign.equalsIgnoreCase(Util.EXITED_ETHER_TOPICS)) {
              tokenId = "0x" + topicsArray.getString(2).substring(24);
              tokenId = ContractUtil.getAddressFromEthHex(tokenId);
            }
            tx.setDestTokenId(tokenId);
            String ownerAddress = getTxOwner(tx.getDestTxid());
            if (StringUtils.isNotBlank(ownerAddress)) {
              tx.setDestTxOwner(ownerAddress);
              result = true;
            } else {
              log.warn("tron get tx owner fail, tx is {}", tx.getDestTxid());
            }
          } else {
            log.error("tron parseDestChainLog error, toAddress is not same with the "
                    + "transaction table,tx hash is {}, parse toAddress is {}, toAddress in table is {}",
                tx.getDestTxid(), toAddress, tx.getToAddress());
          }
        }
      } //_for
    } catch (Exception e) {
      log.error("tron parseDestChainLog throw exception = [{}], source hash {} ",
          Throwables.getStackTraceAsString(e), tx.getSrcTxid());
      // The transaction information cannot be obtained, and the status turn to DEST_CHAIN_HANDLING
      tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLING.getValue());
    }

    if (result) {
      tx.setToBlock(Long.parseLong(txReceipt.getString("blockNumber")));
    }

    return result;
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
        // if there is no new block need to parse, return 0
        return 0L;
      }
    } else {
      // if didn't get the latest block number, return 0
      return 0L;
    }

    return toBlockNum;
  }

  private JSONObject getResponseObject(String reqUrl){
    String respString = "";
    boolean parseError = false;
    for (int i = 0; i < Util.RETRY_TIMES; ++i) {
      respString = HttpClientUtil.getByApiKey(reqUrl, apiKeyName, apiKey);
      if (StringUtils.isBlank(respString)) {
        parseError = true;
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          log.warn("getResponseObject sleep fail, the exception is {}",
              Throwables.getStackTraceAsString(e));
          Thread.currentThread().interrupt();
        }
      } else {
        parseError = false;
        break;
      }
    }
    if (parseError) {
      log.error("Tron get contract event list error! reqUrl: {}", reqUrl);
      return new JSONObject();
    }
    JSONObject resp = JSON.parseObject(respString);
    if (ObjectUtils.isEmpty(resp)) {
      log.error("Tron get contract event list error! reqUrl: {}", reqUrl);
      return new JSONObject();
    }
    return resp;
  }

  private MessageCenter getBasicMessageFromEvent(
      JSONObject data, String contractAddress, String eventType, boolean confirm) {
    MessageCenter message = new MessageCenter();
    message.setTxId(data.getString(CommonConstant.TRON_TRANSACTION_ID));
    message.setBlockNumber(data.getLongValue(CommonConstant.TRON_BLOCK_NUMBER));
    message.setContractAddress(contractAddress);
    message.setEventType(eventType);
    message.setFromChainId("tron");
    message.setToChainId("bttc");
    int status = 0;
    if (confirm) {
      status = 1;
    }
    message.setStatus(status);
    message.setEventIndex(data.getInteger("event_index"));
    message.setContent(data.toJSONString());
    message.setTimeStamp(Util.convertMillSecondsToDay(data.getLongValue("block_timestamp")));

    return message;
  }

  private long getBlockTimeByNumRetry(long blockNum, boolean confirm, int retryTimes) {
    if (retryTimes == 0) {
      return 0;
    }
    long blockTime = 0;
    blockTime = getBlockTimeByNum(blockNum, confirm);
    if (0 == blockTime) {
      retryTimes--;
      if (retryTimes == 0) {
        log.error("get Block Time By Num Retry fail!");
        return 0;
      }

      try {
        // sleep for a random time in case of centralized parallel retries
        int sleepTime = rand.nextInt(500) + 500;
        Thread.sleep(sleepTime);
      } catch (InterruptedException sleepException) {
        log.warn("get Block Time By Num Retry sleep exception! exception: {}",
            Throwables.getStackTraceAsString(sleepException));
        Thread.currentThread().interrupt();
      }

      return getBlockTimeByNumRetry(blockNum, confirm, retryTimes);
    }
    return blockTime;
  }

  private long getBlockTimeByNum(long blockNum, boolean confirm) {
    String timeStamp;
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("num", blockNum);
    String requestUrl = tronGetBlockByNumUrl;
    if (confirm) {
      requestUrl = tronGetBlockByNumUrlSolidity;
    }
    String postResult = HttpClientUtil.doPostJsonWithApiKeyRetry(
        requestUrl,
        jsonObject.toJSONString(),
        apiKeyName,
        apiKey,
        CommonConstant.CONTROLLER_RETRY_TIMES);
    if (StringUtils.isBlank(postResult)) {
      log.warn("tron get block by num  return null, the block number is {}, requestUrl is {}",
          blockNum, requestUrl);
      return 0L;
    }
    JSONObject result;
    try {
      result = JSON.parseObject(postResult);
      if (result.containsKey(CommonConstant.TRON_BLOCK_HEADER)) {
        JSONObject blockHeader = result.getJSONObject(CommonConstant.TRON_BLOCK_HEADER);
        if (MapUtils.isNotEmpty(blockHeader) && blockHeader.containsKey(CommonConstant.TRON_RAW_DATA)) {
          JSONObject rawData = blockHeader.getJSONObject(CommonConstant.TRON_RAW_DATA);
          if (MapUtils.isNotEmpty(rawData) && rawData.containsKey("timestamp")) {
            timeStamp = rawData.getString("timestamp");
            if (StringUtils.isNotBlank(timeStamp)) {
              return Long.valueOf(timeStamp);
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn(
          "tron get block by num fail,the block num is {}, requestUrl is {}, return postResult is {}",
          blockNum, requestUrl, postResult);
      return 0;
    }
    log.warn("tron get block by num fail,the block num is {}, requestUrl is {}, return value is {}",
        blockNum, requestUrl, result);
    return 0;
  }

  private String getReqUrlByFromBlockAndToBlock(
      String contractAddress, String eventName, long fromBlockNum, long toBlockNum,
      boolean confirm) {
    long fromBlockTime = getBlockTimeByNumRetry(fromBlockNum, confirm, 10);
    long toBlockTime = getBlockTimeByNumRetry(toBlockNum, confirm, 10);
    if ((fromBlockTime == 0L) || (toBlockTime == 0L)) {
      log.warn("Tron get from block {} or to to block {} time error! confirm: {}",
          fromBlockNum, toBlockNum, confirm);
      return null;
    }
    String reqUrl = tronAddressEventUrl + contractAddress + "/events?limit=50";
    reqUrl = reqUrl + "&event_name=" + eventName;
    reqUrl = reqUrl + "&min_timestamp=" + Long.toString(fromBlockTime);
    reqUrl = reqUrl + "&max_timestamp=" + Long.toString(toBlockTime);
    reqUrl = reqUrl + "&order_by=block_timestamp,asc";
    if (confirm) {
      reqUrl = reqUrl + "&only_confirmed=true";
    } else {
      reqUrl = reqUrl + "&only_confirmed=false";
    }

    return reqUrl;
  }

  public JSONObject getTransactionReceipt(String txid, boolean confirm) {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.TRON_VALUE, txid);
    String url;
    if (confirm) {
      url = tronGetTransactionInfoByIdUrlSolidity;
    } else {
      url = tronGetTransactionInfoByIdUrl;
    }
    String postResult = HttpClientUtil.doPostJsonWithApiKeyRetry(
        url,
        jsonObject.toJSONString(),
        apiKeyName,
        apiKey,
        CommonConstant.CONTROLLER_RETRY_TIMES);
    if (StringUtils.isBlank(postResult)) {
      return new JSONObject();
    }
    return JSON.parseObject(postResult);
  }

  private boolean checkAlreadyProcessed(JSONObject txReceipt) {
    boolean alreadyProcessed = false;
    try {
      if (MapUtils.isNotEmpty(txReceipt) && txReceipt.containsKey("contractResult")) {
        String contractResult =
            Util.fromHex(txReceipt.getJSONArray("contractResult").getString(0));
        if (contractResult.contains(Util.EXIT_ALREADY_PROCESSED)) {
          alreadyProcessed = true;
        }
      }
    } catch (Exception e) {
      log.error("tron checkAlreadyProcessed throw exception = {}",
          Throwables.getStackTraceAsString(e));
    }

    return alreadyProcessed;
  }

  @Override
  public JSONObject getTransactionByHash(String txid, boolean confirm) {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.TRON_VALUE, txid);
    String url;
    if (confirm) {
      url = tronGetTransactionByIdUrlSolidity;
    } else {
      url = tronGetTransactionByIdUrl;
    }
    String postResult = HttpClientUtil.doPostJsonWithApiKeyRetry(
        url,
        jsonObject.toJSONString(),
        apiKeyName, apiKey, CommonConstant.RETRY_TIMES);
    if (StringUtils.isBlank(postResult)) {
      return new JSONObject();
    }
    return JSON.parseObject(postResult);
  }

  public String getTxOwner(String hash) {
    String ownerAddress = null;
    JSONObject transaction = getTransactionByHash(hash, false);
    if (MapUtils.isNotEmpty(transaction)) {
      JSONObject para = (JSONObject) (transaction.getJSONObject(CommonConstant.TRON_RAW_DATA)
          .getJSONArray("contract").get(0));
      ownerAddress = para.getJSONObject(CommonConstant.TRON_PARAMETER).getJSONObject(CommonConstant.TRON_VALUE)
          .getString(CommonConstant.TRON_OWNER_ADDRESS);
      ownerAddress = TronUtils.convertEthAddressToTronAddress(ownerAddress);
    }
    return ownerAddress;
  }

  @Override
  public String getGasPrice() {
    String value = DEFAULT_TRON_GAS_PRICE;
    String url = tronGridBaseUrl + "getchainparameters";
    String result = HttpClientUtil.getByApiKey(url, apiKeyName, apiKey);
    if (StringUtils.isBlank(result)) {
      return value;
    }
    JSONObject obj = JSON.parseObject(result);
    JSONArray array = obj.getJSONArray("chainParameter");
    for (int i = 0; i < array.size(); i++) {
      JSONObject object = array.getJSONObject(i);
      if (object.getString("key").equals("getEnergyFee")) {
        value = object.getString(CommonConstant.TRON_VALUE);
        break;
      }
    }
    return value;
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
