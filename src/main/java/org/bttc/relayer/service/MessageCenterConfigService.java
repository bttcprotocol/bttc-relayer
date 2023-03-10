package org.bttc.relayer.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.bttc.relayer.bean.dao.MessageCenterConfig;
import org.bttc.relayer.bean.enums.ChainTypeEnum;
import org.bttc.relayer.mapper.MessageCenterConfigMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author tron
 * @date 2021/9/24 9:02
 */
@Slf4j
@Service
public class MessageCenterConfigService  extends
    ServiceImpl<MessageCenterConfigMapper, MessageCenterConfig> {

  @Value("${parseData.tronInitBlockNumber:34209921}")
  private long tronInitBlockNumber;
  @Value("${parseData.ethInitBlockNumber:13339718}")
  private long ethInitBlockNumber;
  @Value("${parseData.bscInitBlockNumber:11343268}")
  private long bscInitBlockNumber;
  @Value("${parseData.bttcInitBlockNumber:1}")
  private long bttcInitBlockNumber;

  private static final String BLOCK_NUMBER_STORAGE_NAME = "BlockNumber";
  private static final String CONTRACT_ADDRESS = "contract_address";
  private static final String CHAIN = "chain";
  @Resource
  private MessageCenterConfigMapper msgCenterConfigMapper;

  public long getBlockNumber(int chainId, boolean confirm) {
    return getHandledBlockNumber(chainId, BLOCK_NUMBER_STORAGE_NAME, confirm);
  }

  public long getHandledBlockNumber(int chainId, String contractAddress, boolean confirm) {
    long blockNumber = 0L;
    String chainName = ChainTypeEnum.fromCode(chainId).chainName;
    if (chainId == ChainTypeEnum.BTT.code) {
      chainName = "bttc";
    }
    String finalChainName = chainName;
    QueryWrapper<MessageCenterConfig> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq(CONTRACT_ADDRESS, contractAddress)
        .and(wrapper -> wrapper.eq(CHAIN, finalChainName));
    MessageCenterConfig config = msgCenterConfigMapper.selectOne(queryWrapper);
    if (ObjectUtils.isNotEmpty(config)) {
      if (confirm) {
        blockNumber = config.getMaxConfirmBlock();
      } else {
        blockNumber = config.getMaxUnconfirmBlock();
      }
    }

    // init
    if (blockNumber == 0L) {
      if (chainId == ChainTypeEnum.TRON.code) {
        blockNumber = tronInitBlockNumber;
      } else if (chainId == ChainTypeEnum.ETHEREUM.code) {
        blockNumber = ethInitBlockNumber;
      } else if (chainId == ChainTypeEnum.BSC.code){
        blockNumber = bscInitBlockNumber;
      } else {
        blockNumber = bttcInitBlockNumber;
      }
    }

    return blockNumber;
  }

  public void setHandledBlockNumber(int chainId, String contractAddress, long toBlock, boolean confirm) {
    String chainName = ChainTypeEnum.fromCode(chainId).chainName;
    if (chainId == ChainTypeEnum.BTT.code) {
      chainName = "bttc";
    }
    String finalChainName = chainName;
    QueryWrapper<MessageCenterConfig> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq(CONTRACT_ADDRESS, contractAddress);
    queryWrapper.and(wrapper -> wrapper.eq(CHAIN, finalChainName));
    MessageCenterConfig config = msgCenterConfigMapper.selectOne(queryWrapper);
    if (ObjectUtils.isNotEmpty(config)) {
      if (confirm) {
        config.setMaxConfirmBlock(toBlock);
      } else {
        config.setMaxUnconfirmBlock(toBlock);
      }
      msgCenterConfigMapper.updateById(config);
    } else {
      config = MessageCenterConfig.builder().build();
      if (confirm) {
        config.setMaxConfirmBlock(toBlock);
      } else {
        config.setMaxUnconfirmBlock(toBlock);
      }
      config.setChain(finalChainName);
      config.setContractAddress(contractAddress);
      msgCenterConfigMapper.insert(config);
    }
  }

}
