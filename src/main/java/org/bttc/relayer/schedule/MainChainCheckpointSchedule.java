package org.bttc.relayer.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.base.Throwables;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.bttc.relayer.bean.dao.CheckPointInfo;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.bean.enums.StatusEnum;
import org.bttc.relayer.mapper.CheckPointInfoMapper;
import org.bttc.relayer.service.BttcParseDataService;
import org.bttc.relayer.service.MessageCenterConfigService;
import org.bttc.relayer.service.TransactionsService;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author tron
 */
@Slf4j
@Component
public class MainChainCheckpointSchedule {

  private static final String MESSAGE_STORAGE_NAME = "MainChainCheckpoint";
  private static final String MESSAGE_STORAGE_NAME_ENHANCE = "MainChainCheckpoint-enhance";

  @Resource
  private TransactionsService transactionsService;

  @Resource
  private MessageCenterConfigService messageCenterConfigService;

  @Resource
  private CheckPointInfoMapper checkpointInfoMapper;

  @Resource
  private BttcParseDataService parseDataService;

  private Map<Integer, String> chainToAddressMap;
  private Map<Integer, String> chainToAddressEnhanceMap;

  @PostConstruct
  private void init() {
    chainToAddressMap = new HashMap<>(3);
    chainToAddressMap.put(ChainTypeEnum.TRON.code, MESSAGE_STORAGE_NAME);
    chainToAddressMap.put(ChainTypeEnum.ETHEREUM.code, MESSAGE_STORAGE_NAME);
    chainToAddressMap.put(ChainTypeEnum.BSC.code, MESSAGE_STORAGE_NAME);

    chainToAddressEnhanceMap = new HashMap<>(3);
    chainToAddressEnhanceMap.put(ChainTypeEnum.TRON.code, MESSAGE_STORAGE_NAME_ENHANCE);
    chainToAddressEnhanceMap.put(ChainTypeEnum.ETHEREUM.code, MESSAGE_STORAGE_NAME_ENHANCE);
    chainToAddressEnhanceMap.put(ChainTypeEnum.BSC.code, MESSAGE_STORAGE_NAME_ENHANCE);
  }

  @XxlJob("mainChainCheckpointUnConfirm")
  public void mainChainCheckpointUnConfirm() {
    long startTime = System.currentTimeMillis();
    long failCount = 0;
    log.info("MainChainCheckpoint unconfirm tx doExecute start.");
    XxlJobHelper.log("MainChainCheckpoint unconfirm tx doExecute start.");
    for (Map.Entry<Integer, String> entry : chainToAddressMap.entrySet()) {
      int chainId = entry.getKey();
      String contractAddress = entry.getValue();
      long handledBlockNumber = 0L;
      try {
        // Get the latest block number that has been processed on this chain
        handledBlockNumber =
            messageCenterConfigService.getHandledBlockNumber(chainId,
                contractAddress, false);
        // parse  checkpoint
        long contractBlockNumber = parseDataService.parseMainChainCheckpoint(
            chainId, handledBlockNumber + 1, false);
        // Save the  latest  main chain  parsed block number to the 'message_center_config'  table
        if (contractBlockNumber > 0) {
          messageCenterConfigService.setHandledBlockNumber(chainId,
              contractAddress, contractBlockNumber, false);
        }
      } catch (Exception e) {
        log.error(
            "MainChainCheckpoint chain:{} unconfirm tx throw exception, from block:{}, exception: {}",
            chainId,
            handledBlockNumber,
            Throwables.getStackTraceAsString(e));
        XxlJobHelper.log("MainChainCheckpoint chain: " + chainId +
            ", unconfirm tx throw exception, from block: " + handledBlockNumber + ", exception: "
            + Throwables.getStackTraceAsString(e));
        failCount++;
      }
    }

    if (failCount == 0) {
      XxlJobHelper.handleSuccess("MainChainCheckpoint unconfirm doExecute success");
      log.info("MainChainCheckpoint unconfirm doExecute success, cost : {} ms",
          System.currentTimeMillis() - startTime);
    } else {
      XxlJobHelper.handleFail(
          "MainChainCheckpoint unconfirm doExecute fail. refer to the error log for details");
      log.info("MainChainCheckpoint unconfirm doExecute fail, cost : {} ms",
          System.currentTimeMillis() - startTime);
    }
  }

  @XxlJob("mainChainCheckpointConfirmed")
  public void mainChainCheckpointConfirmed() {
    long startTime = System.currentTimeMillis();
    long failCount = 0;
    log.info("MainChainCheckpoint confirm tx doExecute start.");
    XxlJobHelper.log("MainChainCheckpoint confirm tx doExecute start.");
    for (Map.Entry<Integer, String> entry : chainToAddressMap.entrySet()) {
      int chainId = entry.getKey();
      String contractAddress = entry.getValue();
      long handledBlockNumber = 0L;
      try {
        // Get the latest block number that has been processed on this chain
        handledBlockNumber =
            messageCenterConfigService.getHandledBlockNumber(chainId,
                contractAddress, true);
        // parse  checkpoint
        long contractBlockNumber =
            parseDataService.parseMainChainCheckpoint(chainId, handledBlockNumber + 1, true);
        // Save the  latest  main chain  parsed block number to the 'message_center_config'  table
        if (contractBlockNumber > 0) {
          messageCenterConfigService.setHandledBlockNumber(chainId,
              contractAddress, contractBlockNumber, true);
        }
      } catch (Exception e) {
        log.error(
            "MainChainCheckpoint chain:{} confirm tx throw exception, from block:{}, exception: {}",
            chainId,
            handledBlockNumber,
            Throwables.getStackTraceAsString(e));
        XxlJobHelper.log("MainChainCheckpoint chain: " + chainId +
            ", confirm tx throw exception, from block: " + handledBlockNumber + ", exception: "
            + Throwables.getStackTraceAsString(e));
        failCount++;
      }
    }

    if (failCount == 0) {
      XxlJobHelper.handleSuccess("MainChainCheckpoint confirm doExecute success");
      log.info("MainChainCheckpoint confirm doExecute success, cost : {} ms",
          System.currentTimeMillis() - startTime);
    } else {
      XxlJobHelper.handleFail(
          "MainChainCheckpoint confirm doExecute fail. refer to the error log for details");
      log.info("MainChainCheckpoint confirm doExecute fail, cost : {} ms",
          System.currentTimeMillis() - startTime);
    }
  }

  @XxlJob("updateTransactionsByMessage")
  public void updateTransactionsByMessage() {
    try {
      long startTime = System.currentTimeMillis();
      log.info("MainChainCheckpoint update transactions start.");
      XxlJobHelper.log("MainChainCheckpoint update transactions start.");
      // tron
      CheckPointInfo checkPointInfo = checkpointInfoMapper
          .getChainMaxCheckPointMessage(ChainTypeEnum.TRON.code);
      if (ObjectUtils.isEmpty(checkPointInfo)) {
        log.error("MainChainCheckpoint update transactions failed: checkPointInfo is null");
        XxlJobHelper.handleFail(
            "MainChainCheckpoint update transactions failed: checkPointInfo is null");
      }
      long maxPackagedBlockNum = checkPointInfo.getEndBlock();
      transactionsService
          .updateTransactionCheckPoint(maxPackagedBlockNum, ChainTypeEnum.TRON.code);

      // eth
      checkPointInfo = checkpointInfoMapper
          .getChainMaxCheckPointMessage(ChainTypeEnum.ETHEREUM.code);
      maxPackagedBlockNum = checkPointInfo.getEndBlock();
      transactionsService
          .updateTransactionCheckPoint(maxPackagedBlockNum, ChainTypeEnum.ETHEREUM.code);

      // bsc
      checkPointInfo = checkpointInfoMapper.getChainMaxCheckPointMessage(ChainTypeEnum.BSC.code);
      maxPackagedBlockNum = checkPointInfo.getEndBlock();
      transactionsService
          .updateTransactionCheckPoint(maxPackagedBlockNum, ChainTypeEnum.BSC.code);
      log.info("MainChainCheckpoint update transactions success, cost : {} ms",
          System.currentTimeMillis() - startTime);
      XxlJobHelper.handleSuccess("MainChainCheckpoint update transactions success");
    } catch (Exception e) {
      log.error("MainChainCheckpoint update transactions throw exception = [{}]",
          Throwables.getStackTraceAsString(e));
      XxlJobHelper.handleFail("MainChainCheckpoint update transactions throw exception "
          + Throwables.getStackTraceAsString(e));
    }
  }

  @XxlJob("mainChainCheckpointEnhance")
  @Transactional(rollbackFor = Exception.class)
  public void mainChainCheckpointEnhance() {
    long startTime = System.currentTimeMillis();
    long failCount = 0;
    log.info("MainChainCheckpoint-enhance doExecute start.");
    XxlJobHelper.log("MainChainCheckpoint-enhance doExecute start.");
    for (Map.Entry<Integer, String> entry : chainToAddressEnhanceMap.entrySet()) {
      int chainId = entry.getKey();
      String contractAddress = entry.getValue();
      try {
        // Get the latest confirm block number that has been processed on this chain
        long handledBlockNumber =
            messageCenterConfigService.getHandledBlockNumber(chainId,
                contractAddress, true);

        long[] contractBlockNumber =
            parseDataService.getCheckPointBlockNumber(chainId, handledBlockNumber + 1);
        // Save the  latest  main chain  parsed block number to the 'message_center_config'  table
        if (contractBlockNumber[0] > handledBlockNumber) {
          messageCenterConfigService.setHandledBlockNumber(chainId,
              contractAddress, contractBlockNumber[0], true);
        }
        // If a new side chain block number is resolved, update the transaction status of the side chain
        if (contractBlockNumber[1] > 0) {
          while ( parseTransactions(chainId, contractBlockNumber[1]) >= 100) {
            //it means done when the processed tx number < 100
          }
        }
      } catch (Exception e) {
        log.error("MainChainCheckpoint-enhance throw exception = [{}]",
            Throwables.getStackTraceAsString(e));
        XxlJobHelper.log("MainChainCheckpoint-enhance, chain: " + chainId +
            ", confirm tx throw exception, exception is : "
            + Throwables.getStackTraceAsString(e));
        failCount++;
      }
    }
    if (failCount == 0) {
      XxlJobHelper.handleSuccess("MainChainCheckpoint-enhance doExecute success");
      log.info("MainChainCheckpoint-enhance doExecute success, cost : {} ms",
          System.currentTimeMillis() - startTime);
    } else {
      XxlJobHelper.handleFail(
          "MainChainCheckpoint-enhance doExecute fail. refer to the error log for details");
      log.info("MainChainCheckpoint-enhance doExecute fail, cost : {} ms",
          System.currentTimeMillis() - startTime);
    }
  }

  private int parseTransactions(int chainId, long blockNumber) {
    int parseCount = 0;
    QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("t_status", StatusEnum.SRC_CHAIN_HANDLED.getValue())
        .and(wrapper -> wrapper.eq("dest_chain_id", chainId))
        .and(wrapper -> wrapper.le("from_block", blockNumber))
        .orderByAsc("src_timestamp")
        .last("limit 100");
    List<Transactions> txs = transactionsService.getBaseMapper().selectList(queryWrapper);

    parseCount = txs.size();
    if (parseCount > 0) {
      for (Transactions tx : txs) {
        tx.setTStatus(StatusEnum.SWAP_CHAIN_HANDLED.getValue());
        log.info("[parseTransactions] transaction: {} status from 1 switch to 2",
            tx.getSrcTxid());
      }
      transactionsService.updateBatchById(txs);
    }

    return parseCount;
  }

}
