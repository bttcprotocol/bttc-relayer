package org.bttc.relayer.burnproof;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.bttc.relayer.burnproof.mpt.MerklePatriciaTrie;
import org.bttc.relayer.burnproof.mpt.Proof;
import org.bttc.relayer.burnproof.mpt.SimpleMerklePatriciaTrie;
import org.bttc.relayer.burnproof.rlp.BytesValueRLPOutput;
import org.bttc.relayer.burnproof.rlp.RLP;
import org.bttc.relayer.client.BttcClient;
import org.bttc.relayer.config.AddressConfig;
import org.bttc.relayer.constant.CommonConstant;
import org.bttc.relayer.schedule.Util;
import org.bttc.relayer.schedule.strategy.impl.bttc.BttcParseData;
import org.bttc.relayer.utils.HttpClientUtil;
import org.bttc.relayer.utils.MathUtils;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;

@Component
@Slf4j
public class ProofUtils {

  @Resource
  private AddressConfig addressConfig;

  @Resource
  private BttcClient bttcClient;

  @Resource
  private BttcParseData bttcParseData;

  private static final ExecutorService executorService = new ThreadPoolExecutor(50, 1000,
      10 * 60 * 1000L,
      TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<>(1000),
      new ThreadFactoryBuilder().setNameFormat("proof-utils-%d").build(),
      new ThreadPoolExecutor.CallerRunsPolicy());

  public String buildBlockProof(long blockNumber, long startBlock, long endBlock) {
    StringBuilder result = new StringBuilder();
    List<String> proofs = getFastMerkleProof(blockNumber, startBlock, endBlock);
    for(String proof : proofs) {
      if(proof.startsWith("0x")) {
        proof = proof.substring(2);
      }
      result.append(proof);
    }
    return result.toString();
  }

  /**
   * @description get merkle proof of the tx in the blockNumber
   * @param blockNumber the block number contains the burn transaction
   * @param startBlock  The start packaged block number of the checkPoint packaging the burning transaction
   * @param endBlock    The end packaged block number of the checkPoint packaging the burning transaction
   * @return merkle proof
   */
  public List<String> getFastMerkleProof(
      long blockNumber, long startBlock, long endBlock) {
    int merkleTreeDepth = (int)Math.ceil(Math.log(endBlock - startBlock + 1)/Math.log(2));

    // We generate the proof root down, whereas we need from leaf up
    List<String> reversedProof = new ArrayList<>();

    long offset = startBlock;
    long targetIndex = blockNumber - offset;
    long leftBound = 0;
    long rightBound = endBlock - offset;
    for (int depth = 0; depth < merkleTreeDepth; depth += 1) {
      int nLeaves = (int)Math.pow (2, merkleTreeDepth - depth);

      // The pivot leaf is the last leaf which is included in the left subtree
      long pivotLeaf = leftBound + nLeaves / 2 - 1;

      if (targetIndex > pivotLeaf) {
        // Get the root hash to the merkle subtree to the left
        long newLeftBound = pivotLeaf + 1;
        // eslint-disable-next-line no-await-in-loop
        String subTreeMerkleRoot = queryRootHash(offset + leftBound, offset + pivotLeaf);
        reversedProof.add(subTreeMerkleRoot);
        leftBound = newLeftBound;
      } else {
        // Things are more complex when querying to the right.
        // Root hash may come some layers down so we need to build a full tree by padding with zeros
        // Some trees may be completely empty

        long newRightBound = Math.min(rightBound, pivotLeaf);

        // Expect the merkle tree to have a height one less than the current layer
        int expectedHeight = merkleTreeDepth - (depth + 1);
        if (rightBound <= pivotLeaf) {
          // Tree is empty so we repeatedly hash zero to correct height
          String subTreeMerkleRoot = recursiveZeroHash(expectedHeight);
          reversedProof.add(subTreeMerkleRoot);
        } else {
          // Height of tree given by RPC node
          int subTreeHeight = (int)Math.ceil(Math.log(rightBound - pivotLeaf)/Math.log(2));

          // Find the difference in height between this and the subtree we want
          int heightDifference = expectedHeight - subTreeHeight;

          // For every extra layer we need to fill 2*n leaves filled with the merkle root of a zero-filled Merkle tree
          // We need to build a tree which has heightDifference layers

          // The first leaf will hold the root hash as returned by the RPC
          // eslint-disable-next-line no-await-in-loop
          String remainingNodesHash = queryRootHash(offset + pivotLeaf + 1, offset + rightBound);
          // The remaining leaves will hold the merkle root of a zero-filled tree of height subTreeHeight
          String leafRoots = recursiveZeroHash(subTreeHeight);

          // Build a merkle tree of correct size for the subtree using these merkle roots
          List<String> leaves = new ArrayList<>(
              Collections.nCopies((int)Math.pow(2, heightDifference), leafRoots.substring(2)));
          leaves.set(0, remainingNodesHash);
          MerkleTree subMerkleTree = new MerkleTree(leaves);
          String subTreeMerkleRoot = subMerkleTree.getRoot();
          reversedProof.add(subTreeMerkleRoot);
        }
        rightBound = newRightBound;
      }
    }
    Collections.reverse(reversedProof);
    return reversedProof;
  }

  /**
   * @description get the receipt proof of the burn transaction
   * @param txReceipt receipt of the burn transaction
   * @return receipt
   */
  public JSONObject getReceiptProof(JSONObject txReceipt) throws InterruptedException {
    MerklePatriciaTrie<Bytes, Bytes> trie = new SimpleMerklePatriciaTrie<>(value -> value);
    JSONObject receiptProof = new JSONObject();
    String blockNumInHex = txReceipt.getString("blockNumber");
    long blockNumber = Long.parseLong(blockNumInHex.substring(2), 16);
    JSONObject blockContent = bttcClient.getBlockByNumber(blockNumInHex);
    if (MapUtils.isEmpty(blockContent) ||
        MapUtils.isEmpty(blockContent.getJSONObject(CommonConstant.RESULT)) ||
        CollectionUtils.isEmpty(blockContent.getJSONObject(CommonConstant.RESULT).getJSONArray("transactions"))) {
      log.error("bttc get block by number fail, blockNumber is {}, result is {}", blockNumber, blockContent);
      return new JSONObject();
    }
    JSONObject blockObj = blockContent.getJSONObject(CommonConstant.RESULT);
    if(MapUtils.isEmpty(blockObj)) {
      log.error("bttc get block by number fail, blockNumber is {}, result is {}", blockNumber, blockContent);
      return new JSONObject();
    }
    String blockHash = blockObj.getString("hash");
    String stateSyncTxHash = getStateSyncTxHash(blockNumber, blockHash);
    List<String> hashList = JSON.parseArray(
        blockObj.getString("transactions"), String.class);
    JSONArray transactionReceiptArray = new JSONArray();
    CountDownLatch countDownLatch = new CountDownLatch(hashList.size());
    CopyOnWriteArrayList<String> stateSyncTxHashList = new CopyOnWriteArrayList<>();
    for (String hash : hashList) {
      executorService.execute(() -> {
        if (stateSyncTxHash.equals(hash)) {
          stateSyncTxHashList.add(hash);
          log.info("hash equals stateSyncTxHash, stateSyncTxHash is {}, burn hash is {}",
              hash, txReceipt.getString(CommonConstant.TRANSACTION_HASH));
          countDownLatch.countDown();
          return;
        }
        JSONObject receipt = bttcParseData.getTransactionReceipt(hash);
        synchronized(transactionReceiptArray) {
          transactionReceiptArray.add(receipt);
        }
        countDownLatch.countDown();
      });
    }
    countDownLatch.await();
    if (transactionReceiptArray.size() != (hashList.size() - stateSyncTxHashList.size())) {
      log.error("get receipts fail, lose some, burn tx is {}, transactionArray is {},"
          + "result is {}", txReceipt.getString(CommonConstant.TRANSACTION_HASH),
           hashList, transactionReceiptArray);
      return new JSONObject();
    }
    for (int i = 0; i < transactionReceiptArray.size(); i++) {
      JSONObject receipt = transactionReceiptArray.getJSONObject(i);
      String transactionIndex = receipt.getString("transactionIndex");
      Bytes path = RLP.encodeOne(hexNumberToByte(transactionIndex));
      Bytes rawReceipt = getReceiptBytes(receipt);
      trie.put(path, rawReceipt);
    }
    String transactionIndex = txReceipt.getString("transactionIndex");
    Bytes  path = RLP.encodeOne(hexNumberToByte(transactionIndex));
    Proof<Bytes> proof = trie.getValueWithProof(path);

    JSONArray rlpProofNodes = new JSONArray();
    List<Bytes> proofRelatedNodes = proof.getProofRelatedNodes();
    if (CollectionUtils.isEmpty(proofRelatedNodes)) {
      log.error("getReceiptProof fail, proof is null, tx is {}", txReceipt.getString(CommonConstant.TRANSACTION_HASH));
      return new JSONObject();
    }
    for (Bytes node : proofRelatedNodes) {
      rlpProofNodes.add(node.toHexString());
    }
    receiptProof.put("rlpProofNodes", rlpProofNodes);
    receiptProof.put("receiptRoot", blockObj.getString("receiptsRoot"));
    receiptProof.put("path", path.toHexString());
    Optional<Bytes> valueOptional = proof.getValue();
    Bytes value;
    if (valueOptional.isPresent()) {
       value = valueOptional.get();
    } else {
      log.error("getReceiptProof fail, value is null, tx is {}", txReceipt.getString(CommonConstant.TRANSACTION_HASH));
      return new JSONObject();
    }
    receiptProof.put("nodeValue", value.toHexString());
    return receiptProof;
  }

  public Bytes getReceiptBytes(JSONObject txReceipt) {
    String status = txReceipt.getString("status");
    if ("0x0".equalsIgnoreCase(status)) {
      status = "0x";
    }
    String cumulativeGasUsed = txReceipt.getString("cumulativeGasUsed");
    String logsBloom = txReceipt.getString("logsBloom");
    JSONArray logs = txReceipt.getJSONArray("logs");
    List<Bytes> logRlpList = new ArrayList<>();
    for (int i = 0; i < logs.size(); i++) {
      JSONObject log = logs.getJSONObject(i);
      String address = log.getString(CommonConstant.ADDRESS);

      JSONArray topics = log.getJSONArray(CommonConstant.TOPICS);
      List<String> topicList = topics.toJavaList(String.class);
      Bytes topicRlp = rlpEncode(topicList);
      String data = log.getString("data");

      final BytesValueRLPOutput out1 = new BytesValueRLPOutput();
      out1.startList();
      out1.writeBytes(hexStringToByte(address));
      out1.writeRaw(topicRlp);
      out1.writeBytes(hexStringToByte(data));
      out1.endList();
      Bytes logRlp =  out1.encoded();
      logRlpList.add(logRlp);
    }
    Bytes logsRlp = rlpRawEncode(logRlpList);
    final BytesValueRLPOutput out2 = new BytesValueRLPOutput();
    out2.startList();
    out2.writeBytes(hexStringToByte(status));
    out2.writeBytes(hexNumberToByte(cumulativeGasUsed));
    out2.writeBytes(hexStringToByte(logsBloom));
    out2.writeRaw(logsRlp);
    out2.endList();
    Bytes rlpResult =  out2.encoded();

    String type = txReceipt.getString("type");
    if (StringUtils.isNotBlank(type) && !"0x".equalsIgnoreCase(type) &&  !"0x0".equalsIgnoreCase(type)) {
      if (1 == type.length() % 2) {
        type = "0" + type.substring(2);
      }
      String tmp = "0x" + type + rlpResult.toHexString().substring(2);
      return Bytes.fromHexString(tmp);
    } else {
      return rlpResult;
    }
  }

  public Bytes hexNumberToByte(String hexNumber) {
    if (StringUtils.isBlank(hexNumber) || "0x".equalsIgnoreCase(hexNumber)) {
      return Bytes.EMPTY;
    }
    if (hexNumber.startsWith("0x")) {
      hexNumber = hexNumber.substring(2);
    }

    BigInteger a = new BigInteger(hexNumber, 16);
    if(a.compareTo(BigInteger.ZERO) == 0) {
      return Bytes.EMPTY;
    }

    if (1 == hexNumber.length() % 2) {
      hexNumber = "0" + hexNumber;
    }
    return Bytes.fromHexString(hexNumber);
  }

   public Bytes hexStringToByte(String hexStr) {
    if (StringUtils.isBlank(hexStr) || "0x".equalsIgnoreCase(hexStr)) {
      return Bytes.EMPTY;
    }

    if (hexStr.startsWith("0x")) {
      hexStr = hexStr.substring(2);
    }
    if (1 == hexStr.length() % 2) {
      hexStr = "0" + hexStr;
    }
    return Bytes.fromHexString(hexStr);
  }

  public String recursiveZeroHash(int n) {
    if (n == 0) {
      return "0x0000000000000000000000000000000000000000000000000000000000000000";
    }
    String subHash = recursiveZeroHash(n - 1);
    if (subHash.startsWith("0x")) {
      subHash = subHash.substring(2);
    }
    String input = subHash + subHash;
    return Hash.sha3(input);
  }

  private String queryRootHash(long startBlock, long endBlock) {
    String bttcUrlPrefix = addressConfig.getBttcUrlPrefix();
    JSONObject map = new JSONObject();
    JSONArray params = new JSONArray();
    params.add(startBlock);
    params.add(endBlock);
    map.put(CommonConstant.JSON_RPC, "2.0");
    map.put(CommonConstant.METHOD, "eth_getRootHash");
    map.put(CommonConstant.PARAMS, params);
    map.put("id", 1);
    String resp = null;
    for (int i = 0; i < Util.RETRY_TIMES; ++i) {
      try {
        resp = HttpClientUtil.doPostJson(bttcUrlPrefix, map.toJSONString());
        if (StringUtils.isNotBlank(resp)) {
          JSONObject logsObj = JSON.parseObject(resp);
          if (logsObj.containsKey(CommonConstant.RESULT)) {
            return logsObj.getString(CommonConstant.RESULT);
          }
        }
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        log.warn("queryRootHash fail, the exception is {}",
            Throwables.getStackTraceAsString(e));
        Thread.currentThread().interrupt();
      }
    }

    log.error("bttc get root hash error, json rpc call return blank, url: {}, json: {}, result is {}",
        bttcUrlPrefix, map.toJSONString(), resp);
    return null;
  }
  // getStateSyncTxHash returns block's tx hash for state-sync receipt
  // Bor blockchain includes extra receipt/tx for state-sync logs,
  // but it is not included in transactionRoot or receiptRoot.
  // So, while calculating proof, we have to exclude them.
  // This is derived from block's hash and number
  // state-sync tx hash = keccak256("matic-bor-receipt-" + block.number + block.hash)
  private String getStateSyncTxHash(long blockNumber, String blockHash) {
    StringBuilder input = new StringBuilder();

    String prefix = MathUtils.asciiToHexString("matic-bor-receipt-"); // prefix for bor receipt
    input.append(prefix);
    String blockNumberInHex = String.format("%016x", blockNumber); // 8 bytes of block number (BigEndian)
    input.append(blockNumberInHex);
    input.append(blockHash.substring(2));
    return Hash.sha3(input.toString());
  }

  private Bytes rlpEncode(List<String> inputList) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    for (String input : inputList) {
      out.writeBytes(hexStringToByte(input));
    }
    out.endList();
    return out.encoded();
  }

  private Bytes rlpRawEncode(List<Bytes> inputList) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    for (Bytes input : inputList) {
      out.writeRaw(input);
    }
    out.endList();
    return out.encoded();
  }
}