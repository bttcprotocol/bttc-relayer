package org.bttc.relayer.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.base.Throwables;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.bttc.relayer.bean.dao.MessageCenter;
import org.bttc.relayer.bean.dao.TokenMap;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.bean.enums.StatusEnum;
import org.bttc.relayer.client.BscClient;
import org.bttc.relayer.client.BttcClient;
import org.bttc.relayer.client.EthClient;
import org.bttc.relayer.config.AddressConfig;
import org.bttc.relayer.constant.CommonConstant;
import org.bttc.relayer.mapper.TransactionsMapper;
import org.bttc.relayer.schedule.Util;
import org.bttc.relayer.utils.ContractUtil;
import org.bttc.relayer.utils.HttpClientUtil;
import org.bttc.relayer.utils.MathUtils;
import org.bttc.relayer.utils.Rlp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tron.common.utils.ByteArray;
import org.apache.commons.collections4.CollectionUtils;
import org.tron.core.capsule.utils.RLPElement;
import org.tron.core.capsule.utils.RLPList;

/**
 * @Author: tron
 * @Date: 2022/1/17 5:07 PM
 */
@Service
@Slf4j
public class TransactionsService extends ServiceImpl<TransactionsMapper, Transactions> {

  @Autowired
  private TransactionsMapper transactionsMapper;

  @Autowired
  private TokenMapService tokenMapService;
  @Autowired
  private EthClient ethClient;
  @Autowired
  private BscClient bscClient;
  @Autowired
  private BttcClient bttcClient;

  private static final String SRC_TXID = "src_txid";
  private final AddressConfig addressConfig;

  private String tronGetTransactionByIdUrl;
  private String apiKeyName;
  private String apiKey;

  public TransactionsService(AddressConfig addressConfig) {
    this.addressConfig = addressConfig;
  }

  @PostConstruct
  public void initAddress() {
    tronGetTransactionByIdUrl = addressConfig.getTronGetTransactionByIdUrl();
    apiKeyName = addressConfig.getApiKeyName();
    apiKey = addressConfig.getApiKey();
  }

  /**
   * <br> Parsing the transactions submitted the burn proof (only parsing solidified blocks) </br>
   * <br> First obtain the input parameter of the calling contract in the main chain transaction through the transaction hash </br>
   * <br> Obtain the block number of the side chain and the index of the block where the side chain transaction is located by parsing the input parameters <br/>
   * <br> Query the block details of the side chain, and get the side chain transaction hash through the index </br>
   */
  public int parseMainChainWithdrawMessage(
      MessageCenter message, String sideChainHash, int toChain) {
    QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq(SRC_TXID, sideChainHash);

    Transactions transactions = transactionsMapper.selectOne(queryWrapper);
    // If it is a transaction that needs to be processed by the relayer,
    // it must be stored in the database, otherwise it will be ignored and not processed
    if (ObjectUtils.isEmpty(transactions)) {
      log.info("Can't find the main chain withdraw tx {} in database", sideChainHash);
      return 1;
    }

    // If the status has been updated to the status of subsequent processing,
    // the status does not need to be updated (to avoid conflicts with the transfer transaction, toBlock is not updated),
    // only the rest of the information is updated
    if ((transactions.getTStatus() == StatusEnum.DEST_CHAIN_HANDLED.getValue())) {
      log.info("Tx {} status already switch to {}", sideChainHash, transactions.getTStatus());
    } else {
      transactions.setTStatus(StatusEnum.DEST_CHAIN_HANDLED.getValue());
      transactions.setToBlock(message.getBlockNumber());
    }

    transactions.setSrcTxid(sideChainHash);
    transactions.setDestTxid(message.getTxId());
    String toAddress = message.getToAddress();
    if (ChainTypeEnum.TRON.code == toChain) {
      toAddress = ContractUtil.getAddressFromEthHex(toAddress);
    }
    transactions.setToAddress(toAddress);
    transactions.setDestTxOwner(message.getFromAddress());
    transactions.setDestChainId(toChain);
    transactions.setDestTokenId(message.getTokenId());
    transactions.setToAmount(message.getAmount());
    transactions.setDestContractRet(CommonConstant.SUCCESS);
    transactions.setDestTimestamp(Util.convertStringDayToDate(message.getTimeStamp()));
    int result = updateBySrcTxid(transactions);
    log.info("Main chain {} parse withdraw transaction success, txid: {}, status: {}, result: {}",
        toChain,
        transactions.getSrcTxid(),
        transactions.getTStatus(),
        result);
    return result;
  }

  /**
   * According to the main chain transaction hash and the main chain id, get the transaction hash of the withdrawal transaction of the side chain
   *
   * @param mainChainTransactionHash The main chain transaction hash that submits the burning proof
   * @param chainId main chain id
   * @return side chain transaction hash
   */
  public String getSideChainWithdrawTransactionHash(String mainChainTransactionHash, int chainId) {
    String inputData = getTransactionInputData(mainChainTransactionHash,
        chainId);
    if (StringUtils.isBlank(inputData)) {
      return "";
    }
    RLPElement rlpElement = Rlp.decode2OneItem(ByteArray.fromHexString(inputData), 68);
    RLPList inputDataList = (RLPList) rlpElement;

    // It is necessary to remove the 0 in front of the hexadecimal block number,
    // first convert the block number to a decimal number, and then convert it to a hexadecimal number
    String blockNumIn10Radix = MathUtils.convertTo10RadixInString(
        ByteArray.toHexString(inputDataList.get(2).getRLPData()));
    String blockNum = MathUtils.convertTo16RadixInString(blockNumIn10Radix);
    int txIndex = Rlp.decodeInt(inputDataList.get(8).getRLPData(), 1);

    JSONObject blockObject = bttcClient.getBlockByNumber(blockNum);
    if (MapUtils.isEmpty(blockObject)) {
      return "";
    }
    JSONObject result = blockObject.getJSONObject(CommonConstant.RESULT);
    if (MapUtils.isNotEmpty(result) &&
        CollectionUtils.isNotEmpty(result.getJSONArray("transactions"))) {
        JSONArray transactions = result.getJSONArray("transactions");
        return transactions.getString(txIndex);
    }
    log.error(
        "bttc get block by num fail, the mainChain hash is {}, the sideChain block is {}, "
            + "the txIndex is {}, the result is {}", mainChainTransactionHash, blockNum, txIndex,
        blockObject);
    return "";
  }

  private String getTransactionInputData(String hash, int chainId) {
    if (chainId == ChainTypeEnum.TRON.code) {
      return getTransactionInputDataTron(hash);
    } else if (chainId == ChainTypeEnum.ETHEREUM.code) {
      return getTransactionInputDataEth(hash);
    } else if (chainId == ChainTypeEnum.BSC.code) {
      return getTransactionInputDataBsc(hash);
    }
    return null;
  }

  private String getTransactionInputDataTron(String hash) {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("value", hash);
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
    try {
      return transactionObject.getJSONObject("raw_data").getJSONArray("contract")
          .getJSONObject(0).getJSONObject("parameter").getJSONObject("value").getString("data");
    } catch (NullPointerException e) {
      log.warn("get InputData error, the hash is {}, result is {}", hash, postResult);
      return null;
    }
  }

  public String getTransactionInputDataEth(String hash) {
    JSONArray params = new JSONArray();
    params.add(hash);

    JSONObject map = new JSONObject();
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_getTransactionByHash");
    map.put(CommonConstant.PARAMS, params);
    map.put("id", 1);

    String maxBlockResp = ethClient.postRetry(map.toJSONString());
    JSONObject respObj = JSON.parseObject(maxBlockResp);
    if (respObj.containsKey(CommonConstant.RESULT) && MapUtils.isNotEmpty(respObj.getJSONObject(CommonConstant.RESULT))) {
      JSONObject result = respObj.getJSONObject(CommonConstant.RESULT);
      return result.getString("input");
    } else {
      log.warn("eth get input data error, the hash is {}, the respObj is {}", hash, respObj);
    }
    return null;
  }

  public String getTransactionInputDataBsc(String hash) {
    JSONArray params = new JSONArray();
    params.add(hash);

    JSONObject map = new JSONObject();
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_getTransactionByHash");
    map.put(CommonConstant.PARAMS, params);
    map.put("id", 1);

    String maxBlockResp = bscClient.postRetry(map.toJSONString());
    JSONObject respObj = JSON.parseObject(maxBlockResp);
    if (respObj.containsKey(CommonConstant.RESULT) && MapUtils.isNotEmpty(respObj.getJSONObject(CommonConstant.RESULT))) {
      JSONObject result = respObj.getJSONObject(CommonConstant.RESULT);
      return result.getString("input");
    } else {
      log.warn("bsc get input data error, the post json is {}, the respObj is {}",
          map.toJSONString(), maxBlockResp);
    }
    return null;
  }

  public int parseSideChainWithdrawMessage(MessageCenter message, boolean confirm) {
    QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq(SRC_TXID, message.getTxId());
    Transactions transactions = transactionsMapper.selectOne(queryWrapper);

    // The relayer node will not receive the http call from the bttc front end,
    // so theoretically there will be no such withdrawal transaction information in the database before
    if (ObjectUtils.isEmpty(transactions)) {
      transactions = new Transactions();
      transactions.setTStatus(StatusEnum.SRC_CHAIN_HANDLED.getValue());
    } else {
      log.error("Bttc withdraw tx already existed in the database, src_hash: {}", message.getTxId());
    }
    transactions.setFromBlock(message.getBlockNumber());
    transactions.setSrcTxid(message.getTxId());
    transactions.setFromAddress(message.getFromAddress());
    String toAddress = message.getToAddress();
    if (ChainTypeEnum.TRON.chainName.equalsIgnoreCase(message.getToChainId())) {
      toAddress = ContractUtil.getAddressFromEthHex(toAddress);
    }
    transactions.setToAddress(toAddress);
    transactions.setSrcChainId(ChainTypeEnum.BTT.code);
    transactions.setDestChainId(
        Objects.requireNonNull(ChainTypeEnum.fromName(message.getToChainId())).code);
    transactions.setSrcTokenId(message.getTokenId());
    TokenMap tokenMap = tokenMapService.getTokenMap(message.getTokenId(), ChainTypeEnum.BTT.code);
    if (ObjectUtils.isNotEmpty(tokenMap)) {
      transactions.setDestTokenId(tokenMap.getMainAddress());
    } else {
      log.error("Get token map error, child chain token id: {}, the hash is {}",
          message.getTokenId(), message.getTxId());
    }
    BigInteger fromAmount = new BigInteger(message.getAmount());
    fromAmount = fromAmount.add(new BigInteger(message.getFee()));
    fromAmount = fromAmount.add(new BigInteger(message.getTsfFee()));
    transactions.setFromAmount(fromAmount.toString());
    transactions.setToAmount(message.getAmount());
    transactions.setFee(message.getFee());
    transactions.setSrcContractRet(CommonConstant.SUCCESS);
    transactions.setSrcTimestamp(Util.convertStringDayToDate(message.getTimeStamp()));
    transactions.setUpdateTime(new Date());
    int result = saveTransactions(transactions);
    log.info(
        "Bttc parse withdraw transaction success, txid: {}, status: {}, "
            + "confirm: {}, result: {}",
        transactions.getSrcTxid(),
        transactions.getTStatus(),
        confirm,
        result);
    return result;
  }
  private int saveTransactions(Transactions transactions) {
      return transactionsMapper.insertSrcTxInfo(transactions);
  }

  public int updateBySrcTxid(Transactions transactions) {
    QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq(SRC_TXID, transactions.getSrcTxid());
    return getBaseMapper().update(transactions, queryWrapper);
  }

  public void updateTransactionCheckPoint(long maxCheckPointBlockNum, int chainId) {
    try {
      List<Transactions> allTransactions = transactionsMapper.updateTransactionCheckPoint(
          maxCheckPointBlockNum, chainId);
      for (Transactions transaction : allTransactions) {
        transaction.setTStatus(StatusEnum.SWAP_CHAIN_HANDLED.getValue());
        log.info("[updateTransactionCheckPoint] transaction: {} status from 1 switch to 2",
            transaction.getSrcTxid());
      }
      if (CollectionUtils.isNotEmpty(allTransactions)) {
        this.updateBatchById(allTransactions);
      }
    } catch (Exception e) {
      log.error("updateTransactionCheckPoint fail, the chain is {}, exception is [{}]",
          chainId, Throwables.getStackTraceAsString(e));
    }
  }

  public int parseMainChainSubmitWithdrawMessage(String srcTxId, String destTxId, String nonce) {
    QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq(SRC_TXID, srcTxId);
    Transactions transactions = transactionsMapper.selectOne(queryWrapper);

    if (ObjectUtils.isNotEmpty(transactions)) {
      transactions.setTStatus(StatusEnum.DEST_CHAIN_HANDLING.getValue());
      transactions.setDestTxid(destTxId);
      transactions.setNonce(nonce);
      transactions.setUpdateTime(new Date());
    } else {
      log.error("Withdraw tx not exist in the database, src_hash: {}", srcTxId);
      return 0;
    }
    int result = updateBySrcTxid(transactions);
    log.info(
        "Parse submit withdraw transaction success, txid: {}, status: {}, result: {}",
        transactions.getSrcTxid(),
        transactions.getTStatus(),
        result);
    return result;
  }

  public int parseMainChainSubmitWithdrawMessage(String srcTxId) {
    QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq(SRC_TXID, srcTxId);
    Transactions transactions = transactionsMapper.selectOne(queryWrapper);

    if (ObjectUtils.isNotEmpty(transactions)) {
      transactions.setTStatus(StatusEnum.DEST_CHAIN_HANDLED.getValue());
      transactions.setUpdateTime(new Date());
    } else {
      log.error("Bttc withdraw tx not exist in the database, src_hash: {}", srcTxId);
      return 0;
    }
    int result = updateBySrcTxid(transactions);
    log.info(
        "Parse submit withdraw transaction success, txid: {}, status: {}, result: {}",
        transactions.getSrcTxid(),
        transactions.getTStatus(),
        result);
    return result;
  }
}
