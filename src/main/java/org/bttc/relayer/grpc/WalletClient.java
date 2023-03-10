package org.bttc.relayer.grpc;

import com.google.common.base.Throwables;
import com.google.protobuf.ByteString;
import io.grpc.Channel;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.commons.collections4.CollectionUtils;
import org.bttc.relayer.utils.TronUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Service;
import org.tron.api.GrpcAPI;
import org.tron.api.GrpcAPI.Return;
import org.tron.api.GrpcAPI.Return.response_code;
import org.tron.api.GrpcAPI.TransactionExtention;
import org.tron.api.WalletGrpc;
import org.tron.api.WalletSolidityGrpc;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Commons;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.contract.SmartContractOuterClass;

/**
 * @author tron
 */
@Service
@Slf4j
//@RefreshScope
public class WalletClient {

  @GrpcClient("GLOBAL")
  private Channel channel;

  public Protocol.Account getAccount(String address) {
    byte[] addressBytes = Commons.decodeFromBase58Check(address);
    if (addressBytes == null) {
      return null;
    }
    ByteString addressBs = ByteString.copyFrom(addressBytes);
    Protocol.Account request = Protocol.Account.newBuilder().setAddress(addressBs).build();
    return WalletGrpc.newBlockingStub(channel).getAccount(request);
  }

  public Protocol.Transaction getTransactionById(String hash) {
    ByteString bsTxid = ByteString.copyFrom(ByteArray.fromHexString(hash));
    GrpcAPI.BytesMessage request = GrpcAPI.BytesMessage.newBuilder().setValue(bsTxid).build();
    Protocol.Transaction transaction = WalletGrpc.newBlockingStub(channel).getTransactionById(request);
    if (CollectionUtils.isEmpty(transaction.getRawData().getContractList())) {
      return null;
    }
    return transaction;
  }

  public Protocol.TransactionInfo getTransactionInfoById(String hash) {
    ByteString bsTxid = ByteString.copyFrom(ByteArray.fromHexString(hash));
    GrpcAPI.BytesMessage request = GrpcAPI.BytesMessage.newBuilder().setValue(bsTxid).build();
    Protocol.TransactionInfo transactionInfo = WalletGrpc.newBlockingStub(channel)
        .getTransactionInfoById(request);
    if (transactionInfo.getId().isEmpty()) {
      return null;
    }
    return transactionInfo;
  }

  public String triggerConstantContract(byte[] contractAddress, byte[] ownerAddress,
                                        byte[] data) {
    SmartContractOuterClass.TriggerSmartContract
        triggerContract = triggerCallContract(ownerAddress, contractAddress, 0,
        data, 0, null);

    GrpcAPI.TransactionExtention transactionExtention = WalletGrpc.newBlockingStub(channel)
        .triggerConstantContract(triggerContract);

    if (transactionExtention == null || !transactionExtention.getResult().getResult()) {
      return null;
    }

    Protocol.Transaction transaction = transactionExtention.getTransaction();
    if (transaction.getRetCount() != 0 &&
        transactionExtention.getConstantResult(0) != null &&
        transactionExtention.getResult() != null) {
      byte[] result = transactionExtention.getConstantResult(0).toByteArray();
      return Hex.toHexString(result);
    }
    return null;
  }

  public TransactionExtention triggerSmartContract(byte[] contractAddress, byte[] ownerAddress,
      byte[] data) {
    SmartContractOuterClass.TriggerSmartContract
        triggerContract = triggerCallContract(ownerAddress, contractAddress, 0,
        data, 0, null);

    GrpcAPI.TransactionExtention transactionExtention = WalletGrpc.newBlockingStub(channel)
        .triggerContract(triggerContract);

    if (transactionExtention == null || !transactionExtention.getResult().getResult()) {
      return null;
    }

    return transactionExtention;
  }

  public Return broadcastTransaction(Transaction request) {
    int i = 10;
    GrpcAPI.Return response = WalletGrpc.newBlockingStub(channel).broadcastTransaction(request);
    while ((!response.getResult())
        && (response.getCode() == response_code.SERVER_BUSY)
        && (i > 0)) {
      i--;
      response = WalletGrpc.newBlockingStub(channel).broadcastTransaction(request);
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        log.warn("tron broadcastTransaction fail, exception is [{}]",
            Throwables.getStackTraceAsString(e));
        Thread.currentThread().interrupt();
        e.printStackTrace();
      }
    }
    return response;
  }

  public static SmartContractOuterClass.TriggerSmartContract triggerCallContract(byte[] address,
                                                                                 byte[] contractAddress,
                                                                                 long callValue, byte[] data, long tokenValue, String tokenId) {
    SmartContractOuterClass.TriggerSmartContract.Builder builder = SmartContractOuterClass.TriggerSmartContract
        .newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(address));
    builder.setContractAddress(ByteString.copyFrom(contractAddress));
    builder.setData(ByteString.copyFrom(data));
    builder.setCallValue(callValue);
    if (tokenId != null && !"".equals(tokenId)) {
      builder.setCallTokenValue(tokenValue);
      builder.setTokenId(Long.parseLong(tokenId));
    }
    return builder.build();
  }

  public Protocol.Block getFullNodeCurrentBlock() {
    return getBlock4Loader(-1, true);
  }

  public Protocol.Block getSolidityNodeCurrentBlock() {
    return getBlock4Loader(-1, false);
  }

  public Protocol.Block getBlock4Loader(long blockNum, boolean full) {
    if (blockNum < 0) {
      if (!full) {
        return WalletSolidityGrpc
            .newBlockingStub(channel).getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
      } else {
        return WalletGrpc.newBlockingStub(channel).getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
      }
    }
    GrpcAPI.NumberMessage.Builder builder = GrpcAPI.NumberMessage.newBuilder();
    builder.setNum(blockNum);
    if (!full) {
      return WalletSolidityGrpc.newBlockingStub(channel).getBlockByNum(builder.build());
    } else {
      return WalletGrpc.newBlockingStub(channel).getBlockByNum(builder.build());
    }
  }

  public Protocol.Block getBlock(long blockNum) {
    if(blockNum < 0) {
      return WalletGrpc.newBlockingStub(channel).getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
    } else {
      GrpcAPI.NumberMessage.Builder builder = GrpcAPI.NumberMessage.newBuilder();
      builder.setNum(blockNum);
      return WalletGrpc.newBlockingStub(channel).getBlockByNum(builder.build());
    }
  }
}

