package org.bttc.relayer.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.bean.enums.StatusEnum;
import org.bttc.relayer.client.SlackClient;
import org.bttc.relayer.mapper.TransactionsMapper;
import org.bttc.relayer.schedule.Util;
import org.bttc.relayer.schedule.strategy.StatusProcessStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionStatusProcessService {
  private Map<Integer, StatusProcessStrategy> strategyMap;

  private final BttcParseDataService bttcParseDataService;
  private final TransactionsMapper transactionsMapper;
  private final MessageCenterConfigService messageCenterConfigService;

  @Resource
  private TransactionsService transactionsService;

  @Resource
  private SlackClient slackClient;

  @Value("${parseData.parseTimeout:8}")
  private int parseTimeout;            // processing timeout (unit: minute)

  @Value("${parseData.confirmTimeout:20}")
  private int confirmTimeout;            // confirm timeout (unit: minute)

  public TransactionStatusProcessService(
      BttcParseDataService bttcParseDataService,
      TransactionsMapper transactionsMapper,
      MessageCenterConfigService messageCenterConfigService) {
    this.bttcParseDataService = bttcParseDataService;
    this.transactionsMapper = transactionsMapper;
    this.messageCenterConfigService = messageCenterConfigService;
  }

  @PostConstruct
  public void addStrategyMap() {
    strategyMap = new HashMap<>(15);
    strategyMap.put(StatusEnum.SRC_CHAIN_HANDLED.getValue(), new TransactionStatusProcessService.SrcChainHandled());
    strategyMap.put(StatusEnum.SWAP_CHAIN_HANDLED.getValue(), new TransactionStatusProcessService.SwapChainHandled());
    strategyMap.put(StatusEnum.DEST_CHAIN_LAUNCHED.getValue(), new TransactionStatusProcessService.DestChainLaunched());
    strategyMap.put(StatusEnum.DEST_CHAIN_ON_CHAIN.getValue(), new TransactionStatusProcessService.DestChainOnChain());
    strategyMap.put(StatusEnum.DEST_CHAIN_HANDLED.getValue(), new TransactionStatusProcessService.DestChainHandled());
    strategyMap.put(StatusEnum.DEST_CHAIN_HANDLING.getValue(), new TransactionStatusProcessService.DestChainHandling());
    strategyMap.put(StatusEnum.DEST_CHAIN_HANDLE_FAILED.getValue(), new TransactionStatusProcessService.DestChainHandleFailed());
    strategyMap.put(StatusEnum.DEST_CHAIN_HANDLE_TIMEOUT.getValue(), new TransactionStatusProcessService.DestChainHandleTimeout());
    strategyMap.put(StatusEnum.DEST_CHAIN_HASH_ERROR.getValue(), new TransactionStatusProcessService.DestChainHashError());
  }

  public void statusProcess(int status, Transactions tx) throws Exception {
    StatusProcessStrategy p = strategyMap.get(status);
    if (ObjectUtils.isNotEmpty(p)) {
      p.statusProcess(tx);
    }
    else {
      log.error("Tx {} status exception : {}", tx.getSrcTxid(), tx.getTStatus());
    }
  }

  class SrcChainHandled implements StatusProcessStrategy {
    @Override
    public void statusProcess(Transactions tx) throws Exception {
      //The source chain has been processed, and it will be processed by the heimdall chain
    }
  }

  class SwapChainHandled implements StatusProcessStrategy {
    @Override
    public void statusProcess(Transactions tx) throws Exception {
      //The heimdal has processed the tx, waiting for the user to receive the asset on the target chain
    }
  }

  class DestChainLaunched implements StatusProcessStrategy {
    @Override
    public void statusProcess(Transactions tx) throws Exception {
      int result;
      // The transaction on the dest chain has been launched, try to get transaction information from the target chain
      result = bttcParseDataService.parseToChainData(tx, tx.getDestChainId(), false);
      if (result == StatusEnum.DEST_CHAIN_HANDLING.getValue()) {
        // If didn't get the transaction information, the status turn to DEST_CHAIN_HANDLING
        tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLING.getValue());
        // record the timestamp when switch to DEST_CHAIN_HANDLING for the first time,
        tx.setUpdateTime(new Date());
        transactionsMapper.updateById(tx);
        log.info("Tx {} status switch to {}", tx.getSrcTxid(), tx.getTStatus());
      } else {
        // In other cases, update the transaction in the database (status has been updated in parseToChainData)
        QueryWrapper<Transactions> transactionsQueryWrapper = new QueryWrapper<>();
        transactionsQueryWrapper.eq("id", tx.getId())
            .and(wrapper -> wrapper.eq("dest_txid", tx.getDestTxid()));
        transactionsMapper.update(tx, transactionsQueryWrapper);
      }
    }
  }

  class DestChainOnChain implements StatusProcessStrategy {
    @Override
    public void statusProcess(Transactions tx) throws Exception {
      int result = bttcParseDataService.parseToChainData(tx, tx.getDestChainId(), true);
      if (result == StatusEnum.DEST_CHAIN_ON_CHAIN.getValue()) {
        //whether it has been confirmed
        boolean isConfirmed = true;
        int destChainId = tx.getDestChainId();
        if (destChainId == ChainTypeEnum.ETHEREUM.code) {
          long solidityBlock = messageCenterConfigService.getBlockNumber(destChainId, true);
          if (tx.getToBlock() > solidityBlock) {
            isConfirmed = false;
          }
        } else if (destChainId == ChainTypeEnum.BSC.code) {
          long solidityBlock = messageCenterConfigService.getBlockNumber(destChainId, true) - 90;
          if (tx.getToBlock() > solidityBlock) {
            isConfirmed = false;
          }
        }
        //if it is confirmed, the status turn to DEST_CHAIN_HANDLED
        if (isConfirmed) {
          tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLED.getValue());
          tx.setUpdateTime(new Date());
          transactionsMapper.updateById(tx);
          log.info("Tx {} handled success, status switch to {}", tx.getSrcTxid(), tx.getTStatus());
          return;
        }
      }
      // in the other case, judge whether it times out
      if (Util.isTimeout(tx.getUpdateTime(), confirmTimeout)) {
        tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLE_TIMEOUT.getValue());
        tx.setUpdateTime(new Date());
        transactionsMapper.updateById(tx);
        String msg = String.format(
            "Dest chain handled timeout, Tx is not confirmed, src hash: %s, switch to: %d",
            tx.getSrcTxid(), tx.getTStatus());
        log.error(msg);
        slackClient.sendTextMessage(msg);
      }
    }
  }

  class DestChainHandled implements StatusProcessStrategy {
    @Override
    public void statusProcess(Transactions tx) throws Exception {
      // the whole transaction is completed
    }
  }

  class DestChainHandling implements StatusProcessStrategy {
    @Override
    public void statusProcess(Transactions tx) throws Exception {
      // The transaction is in processing status, try to get the transaction information from the target chain
      int result = bttcParseDataService.parseToChainData(tx, tx.getDestChainId(), false);
      if (result == StatusEnum.DEST_CHAIN_HANDLING.getValue()) {
        // if get the transaction information from the target chain, judge whether it times out
        if (Util.isTimeout(tx.getUpdateTime(), parseTimeout)) {
          // status turns to DEST_CHAIN_HANDLE_TIMEOUT and update the data in the database
          tx.setTStatus(StatusEnum.DEST_CHAIN_HANDLE_TIMEOUT.getValue());
          tx.setUpdateTime(new Date());
          transactionsMapper.updateById(tx);
          String msg = String.format(
              "Dest chain handled timeout, src hash: %s, dest hash: %s, switch to: %d",
              tx.getSrcTxid(), tx.getDestTxid(), tx.getTStatus());
          log.error(msg);
          slackClient.sendTextMessage(msg);
        }
      } else {
        // In other cases, update the data in the database
        // (the status has been updated in the function processing,
        // and you need to reconfirm the current transaction status in the database before storing)
        QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("src_txid", tx.getSrcTxid());
        Transactions txNow = transactionsService.getBaseMapper().selectOne(queryWrapper);
        if ((txNow.getTStatus() != StatusEnum.DEST_CHAIN_HANDLED.getValue())) {
          transactionsMapper.updateById(tx);
        }
      }
    }
  }

  class DestChainHandleFailed implements StatusProcessStrategy {
    @Override
    public void statusProcess(Transactions tx) throws Exception {
      // The transaction on the dest chain failed.
    }
  }

  class DestChainHandleTimeout implements StatusProcessStrategy {
    @Override
    public void statusProcess(Transactions tx) throws Exception {
      // The transaction on the dest chain timeout
    }
  }

  class DestChainHashError implements StatusProcessStrategy {
    @Override
    public void statusProcess(Transactions tx) throws Exception {
      // The transaction hash on the dest chain is wrong
    }
  }

}
