package org.bttc.relayer.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.base.Throwables;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.bean.enums.StatusEnum;
import org.bttc.relayer.mapper.TransactionsMapper;
import org.bttc.relayer.service.MainChainSubmitWithdrawService;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

/**
 * @author tron
 */
@Slf4j
@Component
public class MainChainSubmitWithdrawSchedule {

  @Resource
  private TransactionsMapper transactionsMapper;

  @Resource
  private MainChainSubmitWithdrawService mainChainSubmitWithdrawService;

  /**
   * submits the burning proof to the main chain(only normal transactions are processed)
   */
  @XxlJob("mainchainSubmitWithdraw")
  public void mainchainSubmitWithdraw() {
    log.info("MainChainSubmitWithdraw start");
    XxlJobHelper.log("MainChainSubmitWithdraw start");
    // select the pending transactions from the database
    QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("t_status", StatusEnum.SWAP_CHAIN_HANDLED.getValue())
        .orderByAsc("src_timestamp")
        .last("limit 10");
    List<Transactions> txs = transactionsMapper.selectList(queryWrapper);

    for (Transactions tx : txs) {
      try {
        mainChainSubmitWithdrawService.submitWithdraw(tx.getDestChainId(), tx, true);
        // sleep for 3 seconds，preventing exceed the gps of the rpc node
        Thread.sleep(3000);
      } catch (Exception e) {
        log.error("MainChainSubmitWithdraw handling failed. the dest chain is {}, " +
                "src txid is {}, exception is {}",
            tx.getDestChainId(), tx.getSrcTxid(), Throwables.getStackTraceAsString(e));
        Thread.currentThread().interrupt();
      }
    }
    XxlJobHelper.handleSuccess("MainChainSubmitWithdraw handling success");
    log.info("MainChainSubmitWithdraw end");
  }

  /**
   * submits burning proof to the main chain(only for transactions that failed before)
   */
  @XxlJob("mainchainSubmitWithdrawFailed")
  public void mainchainSubmitWithdrawForFailed() {
    log.info("MainChainSubmitWithdrawForFailed start");
    XxlJobHelper.log("MainChainSubmitWithdrawForFailed start");
    // select the pending transactions from the database
    QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
    queryWrapper.in("t_status",
        StatusEnum.DEST_CHAIN_HANDLE_FAILED.getValue(),
        StatusEnum.DEST_CHAIN_HANDLE_TIMEOUT.getValue(),
        StatusEnum.DEST_CHAIN_HASH_ERROR.getValue())
        .orderByAsc("src_timestamp")
        .last("limit 50");
    List<Transactions> txs = transactionsMapper.selectList(queryWrapper);

    for (Transactions tx : txs) {
      try {
        mainChainSubmitWithdrawService.submitWithdraw(tx.getDestChainId(), tx, false);
        // sleep for 3 seconds，preventing exceed the gps of the rpc node
        Thread.sleep(3000);
      } catch (Exception e) {
        log.error("MainChainSubmitWithdrawForFailed handling failed. the src chain is {}, " +
                "src txid is {}, exception is {}",
            tx.getSrcChainId(), tx.getSrcTxid(), Throwables.getStackTraceAsString(e));
        Thread.currentThread().interrupt();
      }
    }
    XxlJobHelper.handleSuccess("MainChainSubmitWithdrawForFailed handling success");
    log.info("MainChainSubmitWithdrawForFailed end");
  }

}
