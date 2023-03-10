package org.bttc.relayer.schedule;

import com.google.common.base.Throwables;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.service.BttcParseDataService;
import org.bttc.relayer.service.MessageCenterConfigService;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

/**
 * @author tron
 */
@Slf4j
@Component
public class BttcWithdrawSchedule {

  private static final String MESSAGE_STORAGE_NAME = "BttcWithdraw";
  private static final String MESSAGE_STORAGE_NAME_2 = "BttcWithdrawAdd";

  @Resource
  private MessageCenterConfigService messageCenterConfigService;

  @Resource
  private BttcParseDataService parseDataService;


  @XxlJob("bttcWithdraw")
  public void bttcWithdraw() {
    long startTime = System.currentTimeMillis();
    log.info("BttcWithdraw confirm tx doExecute start.");
    XxlJobHelper.log("BttcWithdraw confirm tx doExecute start.");

    long handledBlockNumber = 0L;
    try {
      // Get the latest block number that has been processed on this chain
      handledBlockNumber = messageCenterConfigService.getHandledBlockNumber(
          ChainTypeEnum.BTT.code, MESSAGE_STORAGE_NAME, true);
      long contractBlockNumber = parseDataService.parseBttcWithdraw(
          ChainTypeEnum.BTT.code, handledBlockNumber + 1, true, false);
      if (contractBlockNumber > 0) {
        messageCenterConfigService.setHandledBlockNumber(ChainTypeEnum.BTT.code,
            MESSAGE_STORAGE_NAME, contractBlockNumber, true);
      }
    } catch (Exception e) {
      log.error(
          "BttcWithdraw for btt confirm tx throw exception, from block:{}, exception: {}",
          handledBlockNumber,
          Throwables.getStackTraceAsString(e));
      XxlJobHelper.handleFail("BttcWithdraw for btt confirm tx handling failed");
    }

    log.info("BttcWithdraw confirm tx doExecute success, cost : {} ms",
        System.currentTimeMillis() - startTime);
    XxlJobHelper.handleSuccess("BttcWithdraw confirm tx doExecute success");
  }

  @XxlJob("bttcWithdrawAdd")
  public void bttcWithdrawAdd() {
    long startTime = System.currentTimeMillis();
    log.info("BttcWithdraw add missed tx doExecute start.");

    long handledBlockNumber = 0L;
    try {
      // Get the latest block number that has been processed on this chain
      handledBlockNumber = messageCenterConfigService.getHandledBlockNumber(
          ChainTypeEnum.BTT.code, MESSAGE_STORAGE_NAME_2, true);
      long contractBlockNumber = parseDataService.parseBttcWithdraw(
          ChainTypeEnum.BTT.code, handledBlockNumber + 1, true, true);
      if (contractBlockNumber > 0) {
        messageCenterConfigService.setHandledBlockNumber(ChainTypeEnum.BTT.code,
            MESSAGE_STORAGE_NAME_2, contractBlockNumber, true);
      }
    } catch (Exception e) {
      log.error(
          "BttcWithdraw add missed tx throw exception, from block:{}, exception: {}",
          handledBlockNumber,
          Throwables.getStackTraceAsString(e));
      XxlJobHelper.handleFail("BttcWithdraw add missed tx handling failed");
    }

    log.info("BttcWithdraw add missed tx doExecute success, cost : {} ms",
        System.currentTimeMillis() - startTime);
    XxlJobHelper.handleSuccess("BttcWithdraw add missed tx doExecute success");
  }

}
