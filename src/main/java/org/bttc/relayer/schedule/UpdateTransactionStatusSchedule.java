package org.bttc.relayer.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.base.Throwables;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.util.List;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.bean.enums.StatusEnum;
import org.bttc.relayer.service.TransactionStatusProcessService;
import org.bttc.relayer.service.TransactionsService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UpdateTransactionStatusSchedule {
  @Resource
  private TransactionsService transactionsService;

  @Resource
  private TransactionStatusProcessService transactionStatusProcessService;

  @XxlJob("updateTransactionStatus")
  public void updateTransactionStatus() {
    long startTime = System.currentTimeMillis();
    log.info("UpdateTransactionStatus normal handling doExecute start.");
    XxlJobHelper.log("UpdateTransactionStatus normal handling doExecute start.");

      // select pending transactions from the database
      QueryWrapper<Transactions> queryWrapper = new QueryWrapper<>();
      queryWrapper.in("t_status",
          StatusEnum.DEST_CHAIN_HANDLING.getValue(),
          StatusEnum.DEST_CHAIN_ON_CHAIN.getValue())
          .orderByAsc("src_timestamp")
          .last("limit 100");
      List<Transactions> txs = transactionsService.getBaseMapper().selectList(queryWrapper);

      for (Transactions tx : txs) {
        try {
          transactionStatusProcessService.statusProcess(tx.getTStatus(), tx);
        } catch (Exception e) {
          log.error(
              "UpdateTransactionStatus normal handling for relayer: process transaction failed. " +
                  "the src chain id is {}, " +
                  "src txid is {}, dest txid is {}, the exception is [{}]",
              tx.getSrcChainId(), tx.getSrcTxid(), tx.getDestTxid(),
              Throwables.getStackTraceAsString(e));
          XxlJobHelper.handleFail("UpdateTransactionStatus normal handling failed");
        }
      }
      log.info("UpdateTransactionStatus normal handling for relayer doExecute success, cost : {} ms",
          System.currentTimeMillis() - startTime);
      XxlJobHelper.handleSuccess("UpdateTransactionStatus normal handling doExecute success");
  }

}
