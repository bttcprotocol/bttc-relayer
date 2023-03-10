package org.bttc.relayer.client;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

/**
 * @Author: tron
 * @Date: 2022/2/24
 */
public interface Client {

  String postRetry(String json);

  EthSendTransaction sendRawTransactionWithRetry(
      RawTransaction rawTransaction, Credentials credentials);

  EthEstimateGas ethEstimateGas(Transaction gasEstimateTx);
}
