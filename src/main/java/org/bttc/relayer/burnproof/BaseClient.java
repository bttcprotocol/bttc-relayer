package org.bttc.relayer.burnproof;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.Collections;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.bttc.relayer.bean.dao.CheckPointInfo;
import org.bttc.relayer.bean.dto.CheckPointInfoDto;
import org.bttc.relayer.burnproof.rlp.BytesValueRLPOutput;
import org.bttc.relayer.client.BttcClient;
import org.bttc.relayer.constant.CommonConstant;
import org.bttc.relayer.mapper.CheckPointInfoMapper;
import org.bttc.relayer.schedule.Util;
import org.bttc.relayer.schedule.strategy.impl.bttc.BttcParseData;
import org.bttc.relayer.service.BttcParseDataService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BaseClient {

  @Resource
  private ProofUtils proofUtils;

  @Resource
  private BttcParseDataService bttcParseDataService;

  @Resource
  private BttcParseData bttcParseData;

  @Resource
  private CheckPointInfoMapper checkPointInfoMapper;

  @Resource
  private BttcClient bttcClient;

  /**
   *
   * @param hash  the burning transaction hash on the side chain
   * @param chainId The main chain ID corresponding to the burning transaction
   * @return proof of burning transaction
   * @description Construct burning proof of the transaction
   */
  public JSONObject buildPayloadForExit(String hash, int chainId) throws InterruptedException {
    JSONObject result = new JSONObject();
    long blockNumber = -1;
    JSONObject txReceipt = bttcParseData.getTransactionReceipt(hash);
    if (MapUtils.isEmpty(txReceipt)) {
      log.error("buildPayloadForExit fail, txReceipt is null, chainId is {}, tx is {}", chainId, hash);
      return new JSONObject();
    }
    int eventIndex = findWithdrawLogIndex(txReceipt);
    if(eventIndex < 0) {
      log.error("buildPayloadForExit fail, there is no withdrawTo event. chainId is {}, tx is {}", chainId, hash);
      return new JSONObject();
    }

    JSONObject txObj = bttcParseData.getTransactionByHash(hash, true);
    if (MapUtils.isEmpty(txObj)) {
      log.error("buildPayloadForExit fail, txObj is null, chainId is {}, tx is {}", chainId, hash);
      return new JSONObject();
    }
    long lastChildBlock = bttcParseDataService.getLastChildBlock(chainId);
    String blockStr = txObj.getString("blockNumber");
    blockNumber = Long.parseLong(blockStr.substring(2), 16);
    if (blockNumber > lastChildBlock) {
      log.error("buildPayloadForExit fail, no checkpoint contains this burn tx, hash is {}, chain id is {}, "
          + "tx blockNumber is {}, lastChildBlock is {}", hash, chainId, blockNumber, lastChildBlock);
      return new JSONObject();
    }
    CheckPointInfoDto checkPointInfoDto = getCheckPointInfo(blockNumber, chainId);
    if (ObjectUtils.isEmpty(checkPointInfoDto)) {
      log.error("buildPayloadForExit fail, get the related checkpoint fail, hash is {}, chain id is {}", hash, chainId);
      return new JSONObject();
    }
    String blockProof = proofUtils
        .buildBlockProof(blockNumber, checkPointInfoDto.getStart(),
            checkPointInfoDto.getEnd());
    if (StringUtils.isBlank(blockProof)) {
      log.error("buildPayloadForExit fail, get block proof fail, chainId is {}, tx hash is {},tx block is {}", chainId, hash, blockNumber);
      return new JSONObject();
    }
    JSONObject receiptProof = proofUtils.getReceiptProof(txReceipt);
    if (MapUtils.isEmpty(receiptProof)) {
      log.error("buildPayloadForExit fail, get receipt proof fail, chainId is {}, tx hash is {},tx block is {}", chainId, hash, blockNumber);
      return new JSONObject();
    }
    String proof = encodePayload( checkPointInfoDto.getCheckPointNum(), blockNumber, blockProof,
         receiptProof,  eventIndex);
    log.info("buildPayloadForExit success, chain id is {}, tx is {}, proof is {}", chainId,
        hash, proof);
    result.put("proof", proof);
    return result;
  }

  public String encodePayload(long checkPointNumber, long blockNumber, String blockProof,
      JSONObject receiptProof, int eventIndex)  {
    String headerNumberStr = "0x" + Long.toHexString(checkPointNumber * 10000L);
    String blockNumInHex = "0x" + Long.toHexString(blockNumber);
    JSONObject blockObj = bttcClient.getBlockByNumber(blockNumInHex);
    JSONObject blockResult = blockObj.getJSONObject("result");
    String timeStamp = blockResult.getString("timestamp");
    String transactionsRoot = blockResult.getString("transactionsRoot");
    String receiptsRoot = blockResult.getString("receiptsRoot");

    JSONArray receiptParentNodes = receiptProof.getJSONArray("rlpProofNodes");
    final BytesValueRLPOutput out2 = new BytesValueRLPOutput();
    out2.startList();
    for(int i = 0; i < receiptParentNodes.size(); i++) {
      out2.writeRaw(proofUtils.hexStringToByte(receiptParentNodes.getString(i)));
    }
    out2.endList();
    Bytes rlpReceiptParentNodes = out2.encoded();

    String path = receiptProof.getString("path");
    String rawReceipt = receiptProof.getString("nodeValue");
    String pathEncode = "0x00" + path.substring(2);
    String logIndexStr = "0x" + Long.toHexString(eventIndex);
    final BytesValueRLPOutput out1 = new BytesValueRLPOutput();
    out1.startList();
    out1.writeBytes(proofUtils.hexStringToByte(headerNumberStr));
    out1.writeBytes(proofUtils.hexStringToByte(blockProof));
    out1.writeBytes(proofUtils.hexNumberToByte(blockNumInHex));
    out1.writeBytes(proofUtils.hexNumberToByte(timeStamp));
    out1.writeBytes(proofUtils.hexStringToByte(transactionsRoot));
    out1.writeBytes(proofUtils.hexStringToByte(receiptsRoot));
    out1.writeBytes(proofUtils.hexStringToByte(rawReceipt));
    out1.writeBytes(rlpReceiptParentNodes);
    out1.writeBytes(proofUtils.hexStringToByte(pathEncode));
    out1.writeBytes(proofUtils.hexNumberToByte(logIndexStr));
    out1.endList();
    Bytes result = out1.encoded();
    return result.toHexString();
  }

  public int findWithdrawLogIndex(JSONObject receipt) {
    int eventIndex = -1;
    JSONArray logs = receipt.getJSONArray("logs");
    for (int i = 0; i< logs.size(); i++) {
      JSONObject log = logs.getJSONObject(i);
      JSONArray topics = log.getJSONArray(CommonConstant.TOPICS);
      String event = topics.getString(0);
      if (event.equalsIgnoreCase(Util.WITHDRAW_TO_TOPICS)) {
        return i;
      }
    }
    return eventIndex;
  }

  private CheckPointInfoDto getCheckPointInfo(long blockNumer, int chainId) {
    CheckPointInfoDto checkPointInfoDto = getCheckPointNumberFromTable(blockNumer, chainId);
    if (ObjectUtils.isNotEmpty(checkPointInfoDto)) {
      // get the checkpoint information from the chain to check again
      CheckPointInfoDto checkPointInfoDto2 = getCheckPointInfoByCheckpoint(checkPointInfoDto.getCheckPointNum(), chainId);
      if (ObjectUtils.isNotEmpty(checkPointInfoDto2)) {
        long start = checkPointInfoDto2.getStart();
        long end = checkPointInfoDto2.getEnd();
        if(blockNumer >= start && blockNumer <= end) {
          return checkPointInfoDto;
        }
      }
    } else {
      checkPointInfoDto = getCheckPointNumberFromChain(blockNumer, chainId);
    }
    return checkPointInfoDto;
  }

  /**
   * Find the checkpoint info that packaged the side chain block from the database
   *
   * @param blockNumer The block number that contains the side chain deposit transaction
   * @param chainId main chain chain id
   * @return the checkpoint info
   */
  private CheckPointInfoDto getCheckPointNumberFromTable(long blockNumer, int chainId) {
    CheckPointInfoDto checkPointInfoDto = new CheckPointInfoDto();
    QueryWrapper<CheckPointInfo> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("chain_id", chainId)
        .le("start_block", blockNumer)
        .ge("end_block", blockNumer);
    CheckPointInfo checkPointInfo = checkPointInfoMapper.selectOne(queryWrapper);
    if (ObjectUtils.isEmpty(checkPointInfo)) {
      return null;
    }
    checkPointInfoDto.setCheckPointNum(checkPointInfo.getCheckPointNum());
    checkPointInfoDto.setStart(checkPointInfo.getStartBlock());
    checkPointInfoDto.setEnd(checkPointInfo.getEndBlock());
    return checkPointInfoDto;
  }

  /**
   * Find the checkpoint info that packaged the side chain block from the chain
   *
   * @param blockNumer The block number that contains the side chain deposit transaction
   * @param chainId main chain chain id
   * @return the checkpoint info
   */
  private CheckPointInfoDto getCheckPointNumberFromChain(long blockNumer, int chainId) {
    CheckPointInfoDto checkPointInfoDto = new CheckPointInfoDto();
    long startCheckPoint = 1L;
    long endCheckPoint = bttcParseDataService.getCurrentHeaderBlock(chainId);
    if (endCheckPoint <= 0) {
      return checkPointInfoDto;
    }
    while (startCheckPoint <= endCheckPoint) {
      long midCheckPointNumber = (startCheckPoint + endCheckPoint) / 2;
      JSONObject checkPointInfo = bttcParseDataService
          .getHeaderBlocks(midCheckPointNumber, chainId);
      if (MapUtils.isEmpty(checkPointInfo)) {
        return checkPointInfoDto;
      }
      long startBlock = checkPointInfo.getLongValue("start");
      long endBlock = checkPointInfo.getLongValue("end");
      if (blockNumer > startBlock && blockNumer < endBlock) {
        checkPointInfoDto.setCheckPointNum(midCheckPointNumber);
        checkPointInfoDto.setStart(startBlock);
        checkPointInfoDto.setEnd(endBlock);
        return checkPointInfoDto;
      } else if (blockNumer > endBlock) {
        startCheckPoint = midCheckPointNumber + 1;
      } else {
        endCheckPoint = midCheckPointNumber - 1;
      }
    }
    return checkPointInfoDto;
  }

  private CheckPointInfoDto getCheckPointInfoByCheckpoint(long checkpointNumber, int chainId) {
    CheckPointInfoDto checkPointInfoDto = new CheckPointInfoDto();
    JSONObject checkPointInfo = bttcParseDataService
        .getHeaderBlocks(checkpointNumber, chainId);
    if (MapUtils.isEmpty(checkPointInfo)) {
      return checkPointInfoDto;
    }
    long startBlock = checkPointInfo.getLongValue("start");
    long endBlock = checkPointInfo.getLongValue("end");
    checkPointInfoDto.setCheckPointNum(checkpointNumber);
    checkPointInfoDto.setStart(startBlock);
    checkPointInfoDto.setEnd(endBlock);
    return checkPointInfoDto;
  }
}
