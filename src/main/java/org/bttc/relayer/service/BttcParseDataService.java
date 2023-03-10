package org.bttc.relayer.service;

import com.alibaba.fastjson.JSONObject;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.Getter;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.schedule.strategy.BttcParseDataStrategy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * @author tron
 */
@Service("BttcParseDataService")
public class BttcParseDataService {
  @Getter
  private Map<Integer, BttcParseDataStrategy> strategyMap;

  private final BttcParseDataStrategy tronParseData;
  private final BttcParseDataStrategy ethParseData;
  private final BttcParseDataStrategy bscParseData;
  private final BttcParseDataStrategy bttcParseData;

  public BttcParseDataService(
      @Qualifier(value = "BttcTronParseData")BttcParseDataStrategy tronParseData,
      @Qualifier(value = "BttcEthParseData")BttcParseDataStrategy ethParseData,
      @Qualifier(value = "BttcBscParseData")BttcParseDataStrategy bscParseData,
      @Qualifier(value = "BttcBttcParseData")BttcParseDataStrategy bttcParseData) {
    this.tronParseData = tronParseData;
    this.ethParseData = ethParseData;
    this.bscParseData = bscParseData;
    this.bttcParseData = bttcParseData;
  }

  @PostConstruct
  public void addStrategyMap() {
    strategyMap = new HashMap<>(4);
    strategyMap.put(ChainTypeEnum.TRON.code, tronParseData);
    strategyMap.put(ChainTypeEnum.ETHEREUM.code, ethParseData);
    strategyMap.put(ChainTypeEnum.BSC.code, bscParseData);
    strategyMap.put(ChainTypeEnum.BTT.code, bttcParseData);
  }

  @SuppressWarnings("squid:S112")
  public long parseMainChainCheckpoint(int chainId, long fromBlockNum, boolean confirm) throws Exception {
    BttcParseDataStrategy dataStrategy = strategyMap.get(chainId);
    return dataStrategy.parseMainChainCheckpoint(fromBlockNum, confirm);
  }

  @SuppressWarnings("squid:S112")
  public int parseMainChainSubmitWithdraw(int chainId, Transactions tx, String payload,
      boolean normal) throws Exception {
    BttcParseDataStrategy dataStrategy = strategyMap.get(chainId);
    return dataStrategy.parseMainChainSubmitWithdraw(tx, payload, normal);
  }

  @SuppressWarnings("squid:S112")
  public long parseMainChainWithdraw(int chainId, long fromBlockNum, boolean chainToken, boolean confirm, boolean add) throws Exception {
    BttcParseDataStrategy dataStrategy = strategyMap.get(chainId);
    return dataStrategy.parseMainChainWithdraw(fromBlockNum, chainToken, confirm, add);
  }

  @SuppressWarnings("squid:S112")
  public long parseBttcWithdraw(int chainId, long fromBlockNum, boolean confirm, boolean addData) throws Exception {
    BttcParseDataStrategy dataStrategy = strategyMap.get(chainId);
    return dataStrategy.parseBttcWithdraw(fromBlockNum, confirm, addData);
  }

  public int parseToChainData(Transactions tx, int toChainId, boolean confirm) {
    BttcParseDataStrategy dataStrategy = strategyMap.get(toChainId);
    return dataStrategy.parseToChainData(tx, confirm);
  }

  public String getTransactionInputData(String hash, int chainId) {
    BttcParseDataStrategy dataStrategy = strategyMap.get(chainId);
    return dataStrategy.getTransactionInput(hash);
  }

  @SuppressWarnings("squid:S112")
  public long[] getCheckPointBlockNumber(int chainId, long fromBlock) throws Exception {
    BttcParseDataStrategy dataStrategy = strategyMap.get(chainId);
    return dataStrategy.getCheckPointBlockNumber(fromBlock);
  }

  public long getBlockNumber(int chainId, boolean confirm) {
    BttcParseDataStrategy dataStrategy = strategyMap.get(chainId);
    return dataStrategy.getBlockNumber(confirm);
  }

  public long getLastChildBlock(int chainId) {
    BttcParseDataStrategy dataStrategy = strategyMap.get(chainId);
    return dataStrategy.getLastChildBlock();
  }

  public long getCurrentHeaderBlock(int chainId) {
    BttcParseDataStrategy dataStrategy = strategyMap.get(chainId);
    return dataStrategy.getCurrentHeaderBlock();
  }

  public JSONObject getHeaderBlocks(long checkPointNumber, int chainId) {
    BttcParseDataStrategy dataStrategy = strategyMap.get(chainId);
    return dataStrategy.getHeaderBlocks(checkPointNumber);
  }

}
