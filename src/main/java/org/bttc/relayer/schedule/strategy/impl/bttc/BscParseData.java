package org.bttc.relayer.schedule.strategy.impl.bttc;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.client.Client;
import org.bttc.relayer.config.AddressConfig;
import org.bttc.relayer.constant.CommonConstant;
import org.bttc.relayer.schedule.Util;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * @Author: tron
 * @Date: 2022/2/21
 */
@Service("BttcBscParseData")
@Slf4j
public class BscParseData extends AbstractEthParseData {

  private final AddressConfig addressConfig;

  public BscParseData(@Qualifier(value = "BscClient") Client client,
      AddressConfig addressConfig) {
    chainId = ChainTypeEnum.BSC.code;
    this.client = client;
    this.addressConfig = addressConfig;
  }

  @PostConstruct
  public void init() {
    this.rootChainManagerProxy = addressConfig.getBscRootChainManagerProxy();
    this.rootChainProxy = addressConfig.getBscRootChainProxy();
    this.chainTokenAddress = addressConfig.getBscChainTokenAddress();
    this.etherPredicateProxy = addressConfig.getBscEtherPredicateProxy();
    this.erc20PredicateProxy = addressConfig.getBscERC20PredicateProxy();
    this.mintableERC20PredicateProxy = addressConfig.getBscMintableERC20PredicateProxy();

    tokenPredicateProxyToEventMap = new HashMap<>(3);
    tokenPredicateProxyToEventMap.put(etherPredicateProxy, Util.LOCKED_ETHER_TOPICS);
    tokenPredicateProxyToEventMap.put(erc20PredicateProxy, Util.LOCKED_ERC20_TOPICS);
    tokenPredicateProxyToEventMap.put(mintableERC20PredicateProxy, Util.LOCKED_MINTABLE_ERC20_TOPICS);

    tokenPredicateProxyList = new ArrayList<>(3);
    tokenPredicateProxyList.add(etherPredicateProxy.toLowerCase());
    tokenPredicateProxyList.add(erc20PredicateProxy.toLowerCase());
    tokenPredicateProxyList.add(mintableERC20PredicateProxy.toLowerCase());

    erc20PredicateProxyList = new ArrayList<>(2);
    erc20PredicateProxyList.add(erc20PredicateProxy.toLowerCase());
    erc20PredicateProxyList.add(mintableERC20PredicateProxy.toLowerCase());

    List<String> exitedErc20EventTopics = new ArrayList<>(2);
    exitedErc20EventTopics.add(Util.EXITED_ERC20_TOPICS);
    exitedErc20EventTopics.add(Util.EXITED_MINTABLE_ERC20_TOPICS);
    exitedErc20EventTopicsList = new ArrayList<>(1);
    exitedErc20EventTopicsList.add(exitedErc20EventTopics);

    defaultGasPrice = addressConfig.getDefaultGasPriceBsc();
    defaultGasPriceLimit = new BigInteger(addressConfig.getDefaultGasPriceLimitBsc());
    defaultGasLimit = new BigInteger(CommonConstant.DEFAULT_BSC_WITHDRAW_GAS_LIMIT);
  }

}
