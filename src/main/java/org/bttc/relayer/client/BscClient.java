package org.bttc.relayer.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Throwables;
import java.security.SecureRandom;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bttc.relayer.constant.CommonConstant;
import org.bttc.relayer.service.EventUrlConfigService;
import org.bttc.relayer.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/**
 * @author tron
 * @date 2021/11/8 3:37
 */
@Slf4j
@Component("BscClient")
public class BscClient implements Client {

  private String bscUrlPrefix;

  private static final int RE_TRY_TIMES = 10;

  private SecureRandom rand = new SecureRandom();

  @Resource
  private EventUrlConfigService eventUrlConfigService;

  @Value("${rpc.bsc.chainId:97}")
  private long chainId;

  @PostConstruct
  void init() {
    List<String> dbList = eventUrlConfigService.queryUrlConfig("bsc");
    if (CollectionUtils.isNotEmpty(dbList)) {
      bscUrlPrefix = dbList.get(0);
    } else {
      bscUrlPrefix = "";
      log.error("BSC event url config table is null");
    }
  }

  @Override
  public String postRetry(String json) {
    if (StringUtils.isBlank(bscUrlPrefix)) {
      log.error("the bsc url is blank");
      return "";
    }
    return postReTryWithRetryTimes(json, RE_TRY_TIMES);
  }

  public String postReTryWithRetryTimes(String json, Integer retryTimes) {
    if (retryTimes <= 0) {
      return "";
    }
    boolean exceeded = false;
    try {
      String postResult = HttpClientUtil.doPostJsonUnCatch(bscUrlPrefix, json);
      JSONObject result = JSON.parseObject(postResult);
      if (result.containsKey("error")) {
        //-32005:"limit exceeded" ; -32604:"too many requests"; -32603:request failed or timed out
        if (postResult.contains("-32005") || postResult.contains("-32604")
            || postResult.contains("-32603")) {
          exceeded = true;
        }
        log.warn("BscClient postReTry error! bsc url: {}, post json: {}, error: {}",
            bscUrlPrefix, json, postResult);
      } else if (result.containsKey("result")) {
        return postResult;
      }
    } catch (Exception e) {
      String stackTraceStr = Throwables.getStackTraceAsString(e);
      if (stackTraceStr.contains("-32005") || stackTraceStr.contains("-32604")
          || stackTraceStr.contains("-32603") || stackTraceStr.contains("502")
          || stackTraceStr.contains("503") || stackTraceStr.contains("504")) {
        exceeded = true;
      }
      log.warn("BscClient postReTry exception! bsc url: {}, post json: {}, exception: {}",
          bscUrlPrefix, json, stackTraceStr);
    }

    // if exceed the gps, update the rpc node
    if (exceeded ) {
      boolean updateResult = updateUrlPrefix();
      if (!updateResult) {
        return "";
      }
    }
    retryTimes--;
    try {
      // sleep for a random time in case of centralized parallel retries
      int sleepTime = rand.nextInt(500) + 500;
      Thread.sleep(sleepTime);
    } catch (InterruptedException e) {
      log.warn("BscClient postReTry sleep exception! exception: {}",
          Throwables.getStackTraceAsString(e));
      Thread.currentThread().interrupt();
    }

    if (retryTimes == 0) {
      log.error("BscClient postReTry fail! post json : {}", json);
    }
    return postReTryWithRetryTimes(json, retryTimes);
  }

  @Override
  public EthSendTransaction sendRawTransactionWithRetry(
      RawTransaction rawTransaction, Credentials credentials)  {
    for (int i = 0; i < CommonConstant.BSC_RETRY_TIMES; ++i) {
      try {
        Web3j web3j = Web3j.build(new HttpService(bscUrlPrefix));
        return web3j.ethSendRawTransaction(
            Numeric
                .toHexString(TransactionEncoder.signMessage(rawTransaction, chainId, credentials)))
            .sendAsync()
            .get();
      } catch (Exception e) {
        log.warn("Web3j ethSendRawTransaction throw exception: {}",
            Throwables.getStackTraceAsString(e));
        Thread.currentThread().interrupt();
      }
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        log.warn("sendRawTransactionWithRetry sleep throw exception: {}",
            Throwables.getStackTraceAsString(e));
        Thread.currentThread().interrupt();
      }
    }
    log.error("Web3j ethSendRawTransaction failed for bsc, bscUrlPrefix:{}, rawTransaction:{}",
        bscUrlPrefix, rawTransaction.toString());
    return null;
  }

  @Override
  public EthEstimateGas ethEstimateGas(Transaction gasEstimateTx) {
    for (int i = 0; i < CommonConstant.BSC_RETRY_TIMES; ++i) {
      try {
        Web3j web3j = Web3j.build(new HttpService(bscUrlPrefix));
        EthEstimateGas gasEstimate = web3j.ethEstimateGas(gasEstimateTx).send();
        if (gasEstimate.hasError()) {
          log.error("Contract error: {}", gasEstimate.getError().getMessage());
        } else {
          log.info("Gas estimate: {}", gasEstimate.getAmountUsed());
        }
        return gasEstimate;
      } catch (Exception e) {
        log.warn("Web3j ethEstimateGas throw exception: {}",
            Throwables.getStackTraceAsString(e));
      }
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        log.warn("sendRawTransactionWithRetry sleep throw exception: {}",
            Throwables.getStackTraceAsString(e));
        Thread.currentThread().interrupt();
      }
    }
    log.error("Web3j ethEstimateGas failed for bsc, ethUrlPrefix:{}, rawTransaction:{}",
        bscUrlPrefix, gasEstimateTx.toString());
    return null;
  }

  private boolean updateUrlPrefix () {
    List<String> dbList = eventUrlConfigService.queryUrlConfig("bsc");
    if (CollectionUtils.isEmpty(dbList)) {
      log.error("BSC event url config table is null");
      return false;
    }
    if (dbList.contains(bscUrlPrefix)) {
      int index = dbList.indexOf(bscUrlPrefix);
      if (index < dbList.size() - 1) {
        index++;
      } else {
        index = 0;
      }
      bscUrlPrefix = dbList.get(index);
    } else {
      bscUrlPrefix = dbList.get(0);
    }
    return true;
  }

}
