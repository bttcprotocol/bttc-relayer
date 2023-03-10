package org.bttc.relayer.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Throwables;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.burnproof.BaseClient;
import org.bttc.relayer.client.SlackClient;
import org.bttc.relayer.constant.CommonConstant;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MainChainSubmitWithdrawService {
  @Resource
  private BttcParseDataService parseDataService;

  @Resource
  private BaseClient baseClient;

  @Resource
  private SlackClient slackClient;

  public int submitWithdraw(int chainId, Transactions tx, boolean normal)
      throws Exception {
    String payloadJava = null;
    try {
      JSONObject payloadJsonObjectJava = baseClient.buildPayloadForExit(tx.getSrcTxid(), chainId);
      if (MapUtils.isNotEmpty(payloadJsonObjectJava)
          && payloadJsonObjectJava.containsKey("proof")) {
        payloadJava = payloadJsonObjectJava.getString("proof");
      }
    } catch (Exception e) {
      log.warn("Get burn proof payload for tx {} chain {} throw exception: {}",
          tx.getSrcTxid(), chainId, Throwables.getStackTraceAsString(e));
      Thread.currentThread().interrupt();
    }
    if (StringUtils.isBlank(payloadJava)) {
      String msg = String.format("Get burn proof payload for tx %s chain %d failed",
          tx.getSrcTxid(), chainId);
      log.error(msg);
      slackClient.sendTextMessage(msg);
      return CommonConstant.RETURN_FAIL;
    }
    return parseDataService.parseMainChainSubmitWithdraw(
        tx.getDestChainId(), tx, payloadJava, normal);
  }
}
