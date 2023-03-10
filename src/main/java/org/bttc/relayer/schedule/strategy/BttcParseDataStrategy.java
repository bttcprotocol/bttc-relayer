package org.bttc.relayer.schedule.strategy;

import com.alibaba.fastjson.JSONObject;
import org.bttc.relayer.bean.dao.Transactions;
import org.bttc.relayer.constant.CommonConstant;

/**
 * @Author: tron
 * @Date: 2022/2/21
 */
public interface BttcParseDataStrategy {

  /**
   * parse the checkpoint transaction on the main chain
   * @param fromBlockNum The start block for this parsing tasks
   * @param confirm whether only parse the confirm blocks
   * @return the max parsed block number this time
   */
  default long parseMainChainCheckpoint(long fromBlockNum, boolean confirm) {
    return 0L;
  }

  /**
   * submits burn proof on the main chain
   * @param tx the tx info saved in the talbe
   * @param payload the burn proof
   * @param normal whether normal submitting or retry for the timeout tx.
   * @return the result of submitting result
   */
  default int parseMainChainSubmitWithdraw(Transactions tx, String payload,
      boolean normal) {
    return CommonConstant.RETURN_SUCCESS;
  }


  /**
   * parse the transaction that submits burn proof on the main chain
   * @param fromBlockNum The start block for this parsing tasks
   * @param chainToken
   * @param confirm whether only parse the confirm blocks
   * @param add whether Supplementary parse
   * @return the max parsed block number this time
   */
  default long parseMainChainWithdraw(long fromBlockNum, boolean chainToken, boolean confirm, boolean add) {
    return 0L;
  }

  /**
   * parse the withdraw transaction on the bttc chain
   * @param fromBlockNum The start block for this parsing tasks
   * @param confirm whether only parse the confirm blocks
   * @param addData whether Supplementary parse
   * @return the max parsed block number this time
   */
  default long parseBttcWithdraw(long fromBlockNum, boolean confirm, boolean addData) {
    return 0L;
  }

  /**
   * Parse dest chain transaction
   *
   * @param tx  transaction information
   * @param confirm true-transaction is confirmed; false-transaction is unconfirmed
   * @return transaction status
   */
  default int parseToChainData(Transactions tx, boolean confirm) {
    return CommonConstant.RETURN_FAIL;
  }

  /**
   * Obtain the input parameters of this transaction when call contract function
   *
   * @param hash transaction hash
   * @return the input parameters of the transaction
   */
  default String getTransactionInput(String hash) {
    return null;
  }

  JSONObject getTransactionByHash(String hash, boolean confirm);

  /**
   * Get the related main chain and side chain block number of the latest checkpoint
   * @param fromBlockNum The start block for this parsing tasks
   * @return blockNumber[0] main chain block number contains the checkpoint,
   *         blockNumber[1] max side chain block number that packaged in the checkpoint
   */
  default long[] getCheckPointBlockNumber(long fromBlockNum) {
    return new long[]{0L, 0L};
  }

  /**
   * get the latest block number of the chain
   * @param confirm whether get the latest confirmed block number of the chain
   * @return the latest block number of the chain
   */
  long getBlockNumber(boolean confirm);

  /**
   * get the last nonce
   *
   * @param accountAddress the account address
   */
  /**
   * get the max nonce of the account
   * @param accountAddress the account address
   * @param normal whether get the max nonce containing the pending txs
   * @return the max nonce of the account
   */
  default String getAccountLastNonce(String accountAddress, boolean normal) {
    return "1";
  }

  /**
   * get the GasPrice of the chain
   */
  default String getGasPrice()  {
    return "";
  }

  /**
   * get the max block number of the side chain that packaged in the latest checkpoint
   * @return
   */
  default long getLastChildBlock() {
    return -1;
  }

  /**
   * get the max checkpoint number on the main chain
   * @return
   */
  default long getCurrentHeaderBlock() {
    return -1;
  }

  /**
   * get the checkpoint info of the specified checkpoint
   * @param checkPointNumber
   * @return
   */
  default JSONObject getHeaderBlocks(long checkPointNumber) {
    return new JSONObject();
  }
}
