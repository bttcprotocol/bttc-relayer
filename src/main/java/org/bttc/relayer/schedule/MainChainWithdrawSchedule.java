package org.bttc.relayer.schedule;

import com.google.common.base.Throwables;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.service.BttcParseDataService;
import org.bttc.relayer.service.MessageCenterConfigService;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * @author tron
 */
@Slf4j
@Component
public class MainChainWithdrawSchedule {

  private static final String MESSAGE_STORAGE_NAME_CHAIN_TOKEN = "MainChainWithdraw-chaintoken";
  private static final String MESSAGE_STORAGE_NAME_ERC = "MainChainWithdraw-erc";

  private static final String MESSAGE_STORAGE_NAME_CHAIN_TOKEN_ADD = "MainChainWithdraw-chaintoken-Add";
  private static final String MESSAGE_STORAGE_NAME_ERC_ADD = "MainChainWithdraw-erc-Add";

  @Resource
  private MessageCenterConfigService messageCenterConfigService;

  @Resource
  private BttcParseDataService parseDataService;

  private List<Integer> chainIdList;

  @PostConstruct
  private void init() {
    chainIdList = new ArrayList<>(3);
    chainIdList.add(ChainTypeEnum.TRON.code);
    chainIdList.add(ChainTypeEnum.ETHEREUM.code);
    chainIdList.add(ChainTypeEnum.BSC.code);
  }

  @XxlJob("mainChainWithdraw")
  @SuppressWarnings({"squid:S112", "squid:S1192"})
  public void mainChainWithdraw() {
    long startTime = System.currentTimeMillis();
    long failCount = 0;
    log.info("MainChainWithdraw confirm tx doExecute start.");
    XxlJobHelper.log("MainChainWithdraw tx doExecute start.");
    for (Integer chainId : chainIdList) {
      long handledBlockNumberChainToken = 0L;
      try {
        // Get the latest block number that has been processed on this chain
        handledBlockNumberChainToken = messageCenterConfigService.getHandledBlockNumber(
            chainId, MESSAGE_STORAGE_NAME_CHAIN_TOKEN, true);
        // Process  withdrawal transaction on the main chain
        long contractBlockNumber = parseDataService.parseMainChainWithdraw(
            chainId, handledBlockNumberChainToken + 1, true, true, false);
        // Save the  latest  main chain  parsed block number to the 'message_center_config'  table
        if (contractBlockNumber > 0) {
          messageCenterConfigService.setHandledBlockNumber(chainId,
              MESSAGE_STORAGE_NAME_CHAIN_TOKEN, contractBlockNumber, true);
        }
      } catch (Exception e) {
        log.error(
            "MainChainWithdraw chain: {} for chain token confirm tx throw exception, from block:{}, exception: {}",
            chainId,
            handledBlockNumberChainToken,
            Throwables.getStackTraceAsString(e));
        XxlJobHelper.log("MainChainWithdraw chain: " + chainId
            + ", for chain token confirm tx throw exception, from block "
            + handledBlockNumberChainToken + " exception: {}"
            + Throwables.getStackTraceAsString(e));
        failCount++;
      }
      long handledBlockNumberErc = 0L;
      try {
        // Get the latest block number that has been processed on this chain
        handledBlockNumberErc = messageCenterConfigService.getHandledBlockNumber(
            chainId, MESSAGE_STORAGE_NAME_ERC, true);
        // Process  withdrawal transaction on the main chain
        long contractBlockNumber = parseDataService.parseMainChainWithdraw(
            chainId, handledBlockNumberErc + 1, false, true, false);
        // Save the  latest  main chain  parsed block number to the 'message_center_config'  table
        if (contractBlockNumber > 0) {
          messageCenterConfigService.setHandledBlockNumber(chainId,
              MESSAGE_STORAGE_NAME_ERC, contractBlockNumber, true);
        }
      } catch (Exception e) {
        log.error(
            "MainChainWithdraw chain: {} for eth confirm tx throw exception, from block:{}, exception: {}",
            chainId,
            handledBlockNumberErc,
            Throwables.getStackTraceAsString(e));
        XxlJobHelper.log("MainChainWithdraw chain: " + chainId
            + ", for eth confirm tx throw exception, from block " + handledBlockNumberChainToken
            + " exception: {}"
            + Throwables.getStackTraceAsString(e));
        failCount++;
      }
    }

    if (failCount == 0) {
      XxlJobHelper.handleSuccess("MainChainWithdraw confirm tx doExecute success");
      log.info("MainChainWithdraw confirm tx doExecute success, cost : {} ms",
          System.currentTimeMillis() - startTime);
    } else {
      XxlJobHelper.handleFail(
          "MainChainWithdraw confirm tx  doExecute fail. refer to the error log for details");
      log.info("MainChainWithdraw confirm tx  doExecute fail, cost : {} ms",
          System.currentTimeMillis() - startTime);
    }
  }

  @XxlJob("mainChainWithdrawAdd")
  public void mainChainWithdrawAdd() {
    long startTime = System.currentTimeMillis();
    long failCount = 0;
    log.info("mainChainWithdrawAdd confirm tx doExecute start.");
    XxlJobHelper.log("mainChainWithdrawAdd tx doExecute start.");
    for (Integer chainId : chainIdList) {
      long handledBlockNumberChainToken = 0L;
      try {
        // Get the latest block number that has been processed on this chain
        handledBlockNumberChainToken = messageCenterConfigService.getHandledBlockNumber(
            chainId, MESSAGE_STORAGE_NAME_CHAIN_TOKEN_ADD, true);
        // Process  withdrawal transaction on the main chain
        long contractBlockNumber = parseDataService.parseMainChainWithdraw(
            chainId, handledBlockNumberChainToken + 1, true, true, true);
        // Save the  latest  main chain  parsed block number to the 'message_center_config'  table
        if (contractBlockNumber > 0) {
          messageCenterConfigService.setHandledBlockNumber(chainId,
              MESSAGE_STORAGE_NAME_CHAIN_TOKEN_ADD, contractBlockNumber, true);
        }
      } catch (Exception e) {
        log.error(
            "mainChainWithdrawAdd chain: {} for chain token confirm tx throw exception, from block:{}, exception: {}",
            chainId,
            handledBlockNumberChainToken,
            Throwables.getStackTraceAsString(e));
        XxlJobHelper.log("mainChainWithdrawAdd chain: " + chainId
            + ", for chain token confirm tx throw exception, from block "
            + handledBlockNumberChainToken + " exception: {}"
            + Throwables.getStackTraceAsString(e));
        failCount++;
      }
      long handledBlockNumberErc = 0L;
      try {
        // Get the latest block number that has been processed on this chain
        handledBlockNumberErc = messageCenterConfigService.getHandledBlockNumber(
            chainId, MESSAGE_STORAGE_NAME_ERC_ADD, true);
        // Process  withdrawal transaction on the main chain
        long contractBlockNumber = parseDataService.parseMainChainWithdraw(
            chainId, handledBlockNumberErc + 1, false, true, true);
        // Save the  latest  main chain  parsed block number to the 'message_center_config'  table
        if (contractBlockNumber > 0) {
          messageCenterConfigService.setHandledBlockNumber(chainId,
              MESSAGE_STORAGE_NAME_ERC_ADD, contractBlockNumber, true);
        }
      } catch (Exception e) {
        log.error(
            "mainChainWithdrawAdd chain: {} for eth confirm tx throw exception, from block:{}, exception: {}",
            chainId,
            handledBlockNumberErc,
            Throwables.getStackTraceAsString(e));
        XxlJobHelper.log("mainChainWithdrawAdd chain: " + chainId
            + ", for eth confirm tx throw exception, from block " + handledBlockNumberChainToken
            + " exception: {}"
            + Throwables.getStackTraceAsString(e));
        failCount++;
      }
    }

    if (failCount == 0) {
      XxlJobHelper.handleSuccess("mainChainWithdrawAdd confirm tx doExecute success");
      log.info("mainChainWithdrawAdd confirm tx doExecute success, cost : {} ms",
          System.currentTimeMillis() - startTime);
    } else {
      XxlJobHelper.handleFail(
          "mainChainWithdrawAdd confirm tx  doExecute fail. refer to the error log for details");
      log.info("mainChainWithdrawAdd confirm tx  doExecute fail, cost : {} ms",
          System.currentTimeMillis() - startTime);
    }
  }
}
