package org.bttc.relayer.config;

import javax.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author tron
 */
@Data
@Component
public class AddressConfig {

  @Value("${client.tron.api.name:'TrongridApiKey'}")
  private String apiKeyName;
  @Value("${client.tron.api.key:'6e2d8c51-4ef2-4623-9ff4-3d1e46a3034a'}")
  private String apiKey;

  @Value("${client.tron.baseurl:null}")
  private String tronBaseUrl;
  @Value("${client.tron.baseurl-solidity:null}")
  private String tronBaseUrlSolidity;
  @Value("${client.tron.addresseventurl:null}")
  private String tronAddressEventUrl;
  @Value("${client.tron.txeventurl:null}")
  private String tronTxEventUrl;

  private String tronGetBlockByNumUrl;
  private String tronGetBlockByNumUrlSolidity;
  private String tronGetTransactionByIdUrl;
  private String tronGetTransactionByIdUrlSolidity;
  private String tronGetTransactionInfoByIdUrl;
  private String tronGetTransactionInfoByIdUrlSolidity;
  private String tronGetNowBlockUrl;
  private String tronGetNowBlockUrlSolidity;

  @Value("${client.bttc.eventurl:null}")
  private String bttcUrlPrefix;
  @Value("${client.bttc.url:null}")
  private String bttcUrl;

  @Value("${parseData.trxToken:TZDXJyYhSjM8T4cUYqGj2yib718E7ZmGQc}")
  private String tronChainTokenAddress;
  @Value("${parseData.ethToken:0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE}")
  private String ethChainTokenAddress;
  @Value("${parseData.bnbToken:0xff00000000000000000000000000000000000002}")
  private String bscChainTokenAddress;
  @Value("${parseData.bttToken:0x0000000000000000000000000000000000001010}")
  private String bttChainTokenAddress;

  @Value("${parseData.tronRootChainManagerProxy:null}")
  private String tronRootChainManagerProxy;
  @Value("${parseData.ethRootChainManagerProxy:null}")
  private String ethRootChainManagerProxy;
  @Value("${parseData.bscRootChainManagerProxy:null}")
  private String bscRootChainManagerProxy;

  @Value("${parseData.tronRootChainProxy:null}")
  private String tronRootChainProxy;
  @Value("${parseData.ethRootChainProxy:null}")
  private String ethRootChainProxy;
  @Value("${parseData.bscRootChainProxy:null}")
  private String bscRootChainProxy;

  @Value("${parseData.tronEtherPredicateProxy:null}")
  private String tronEtherPredicateProxy;
  @Value("${parseData.ethEtherPredicateProxy:null}")
  private String ethEtherPredicateProxy;
  @Value("${parseData.bscEtherPredicateProxy:null}")
  private String bscEtherPredicateProxy;

  @Value("${parseData.tronERC20PredicateProxy:null}")
  private String tronERC20PredicateProxy;
  @Value("${parseData.ethERC20PredicateProxy:null}")
  private String ethERC20PredicateProxy;
  @Value("${parseData.bscERC20PredicateProxy:null}")
  private String bscERC20PredicateProxy;

  @Value("${parseData.tronMintableERC20PredicateProxy:null}")
  private String tronMintableERC20PredicateProxy;
  @Value("${parseData.ethMintableERC20PredicateProxy:null}")
  private String ethMintableERC20PredicateProxy;
  @Value("${parseData.bscMintableERC20PredicateProxy:null}")
  private String bscMintableERC20PredicateProxy;

  @Value("${parseData.tronEtherPredicateProxyInEth:null}")
  private String tronEtherPredicateProxyInEth;
  @Value("${parseData.tronERC20PredicateProxyInEth:null}")
  private String tronERC20PredicateProxyInEth;
  @Value("${parseData.tronMintableERC20PredicateProxyInEth:null}")
  private String tronMintableERC20PredicateProxyInEth;

  @Value("${parseData.tronStakingInfo:null}")
  private String tronStakingInfo;

  @Value("${parseData.EventsHubProxy:null}")
  private String eventsHubProxy;

  @Value("${btt.backend.postUrl:null}")
  private String bttcBackendPostUrl;

  @PostConstruct
  void init() {
    tronGetBlockByNumUrl = tronBaseUrl + "getblockbynum";
    tronGetBlockByNumUrlSolidity = tronBaseUrlSolidity + "getblockbynum";
    tronGetTransactionByIdUrl = tronBaseUrl + "gettransactionbyid";
    tronGetTransactionByIdUrlSolidity = tronBaseUrlSolidity + "gettransactionbyid";
    tronGetTransactionInfoByIdUrl = tronBaseUrl + "gettransactioninfobyid";
    tronGetTransactionInfoByIdUrlSolidity = tronBaseUrlSolidity + "gettransactioninfobyid";
    tronGetNowBlockUrl = tronBaseUrl + "getnowblock";
    tronGetNowBlockUrlSolidity = tronBaseUrlSolidity + "getnowblock";
  }

  @Value("${parseData.bridge.trxToken:null}")
  private String bridgeTrxAddressConfig;
  @Value("${parseData.bridge.ethToken:null}")
  private String bridgeEthAddressConfig;
  @Value("${parseData.bridge.eth:null}")
  private String parseContractAddress;
  @Value("${parseData.bridge.tron:null}")
  private String parseTronContractAddress;

  @Value("${client.bttc.ChildERC20Exit:null}")
  private String childERC20Exit;

  @Value("${tron.transaction.urlPrefix:null}")
  private String tronTxUrlPrefixConfig;
  @Value("${tron.token.urlPrefix:null}")
  private String tronTokenUrlPrefixConfig;
  @Value("${eth.transaction.urlPrefix:null}")
  private String ethTxUrlPrefixConfig;
  @Value("${eth.token.urlPrefix:null}")
  private String ethTokenUrlPrefixConfig;
  @Value("${bsc.transaction.urlPrefix:null}")
  private String bscTxUrlPrefixConfig;
  @Value("${bsc.token.urlPrefix:null}")
  private String bscTokenUrlPrefixConfig;
  @Value("${btt.transaction.urlPrefix:null}")
  private String bttcTxUrlPrefixConfig;
  @Value("${btt.token.urlPrefix:null}")
  private String bttcTokenUrlPrefixConfig;
  @Value("${tron.token.trxUrl:null}")
  private String trxUrlConfig;
  @Value("${eth.token.ethUrl:null}")
  private String ethUrlConfig;
  @Value("${bsc.token.bnbUrl:null}")
  private String bnbUrlConfig;
  @Value("${btt.token.bttUrl:null}")
  private String bttUrlConfig;

  @Value("${relayer.address.bttc:null}")
  private String relayerBttcAddress;
  @Value("${relayer.key:null}")
  private String relayerKey;

  @Value("${relayer.js.url:null}")
  private String jsLibUrl;
  @Value("${relayer.js.port:null}")
  private String jsLibPort;

  @Value("${relayer.defaultGasPrice.eth:null}")
  private String defaultGasPriceEth;
  @Value("${relayer.defaultGasPrice.bsc:null}")
  private String defaultGasPriceBsc;
  @Value("${relayer.defaultGasPriceLimit.eth:null}")
  private String defaultGasPriceLimitEth;
  @Value("${relayer.defaultGasPriceLimit.bsc:null}")
  private String defaultGasPriceLimitBsc;
  @Value("${relayer.transferFee.eth:null}")
  private String transferFeeEth;
  @Value("${relayer.transferFee.bsc:null}")
  private String transferFeeBsc;
  @Value("${relayer.transferFee.tron:null}")
  private String transferFeeTron;

}
