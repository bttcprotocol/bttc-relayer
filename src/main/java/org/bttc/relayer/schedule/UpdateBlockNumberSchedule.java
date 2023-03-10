package org.bttc.relayer.schedule;

import com.google.common.base.Throwables;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.service.BttcParseDataService;
import org.bttc.relayer.service.MessageCenterConfigService;
import org.springframework.stereotype.Component;

/**
 * @author tron
 */
@Slf4j
@Component
public class UpdateBlockNumberSchedule {

  private static final String MESSAGE_STORAGE_NAME = "BlockNumber";

  @Resource
  private MessageCenterConfigService messageCenterConfigService;

  @Resource
  private BttcParseDataService parseDataService;

  private List<Integer> chainIdList;

  @PostConstruct
  private void init() {
    chainIdList = new ArrayList<>(4);
    chainIdList.add(ChainTypeEnum.TRON.code);
    chainIdList.add(ChainTypeEnum.ETHEREUM.code);
    chainIdList.add(ChainTypeEnum.BSC.code);
    chainIdList.add(ChainTypeEnum.BTT.code);
  }

  @XxlJob("updateBlockNumber")
  public void updateBlockNumber() {
    long startTime = System.currentTimeMillis();
    long failCount = 0;
    log.info("UpdateBlockNumber tx doExecute start.");
    XxlJobHelper.log("UpdateBlockNumber tx doExecute start.");

    for (Integer chainId : chainIdList) {
      try {
        // Get the latest block number on the chain
        long blockNumber = parseDataService.getBlockNumber(chainId, false);
        //the latest block number on the chain
        long solidityBlockNum = 0;
        if (ChainTypeEnum.BTT.code.equals(chainId)) {
          solidityBlockNum = blockNumber - 64;
        } else if (ChainTypeEnum.BSC.code.equals(chainId)) {
          solidityBlockNum = blockNumber - 16;
        } else {
          solidityBlockNum = parseDataService.getBlockNumber(chainId, true);
        }
        // save the latest block number in the 'message_center_config' table
        if (blockNumber > 0) {
          messageCenterConfigService.setHandledBlockNumber(chainId,
              MESSAGE_STORAGE_NAME, blockNumber, false);
        }
        if (solidityBlockNum > 0) {
          messageCenterConfigService.setHandledBlockNumber(chainId,
              MESSAGE_STORAGE_NAME, solidityBlockNum, true);
        }
      } catch (Exception e) {
        log.error(
            "UpdateBlockNumber chain:{} for get block number throw exception, exception: {}",
            chainId,
            Throwables.getStackTraceAsString(e));
        XxlJobHelper.log("UpdateBlockNumber chain: " + chainId
            + ", for get block number throw exception, exception: {}"
            + Throwables.getStackTraceAsString(e));
        failCount++;
      }
    }
    if (failCount == 0) {
      XxlJobHelper.handleSuccess("UpdateBlockNumber doExecute success");
      log.info("UpdateBlockNumber doExecute success, cost : {} ms",
          System.currentTimeMillis() - startTime);
    } else {
      XxlJobHelper.handleFail(
          "UpdateBlockNumber doExecute fail. refer to the error log for details");
      log.info("UpdateBlockNumber doExecute fail, cost : {} ms",
          System.currentTimeMillis() - startTime);
    }
  }
}
