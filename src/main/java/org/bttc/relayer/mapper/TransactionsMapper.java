package org.bttc.relayer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.bttc.relayer.bean.dao.Transactions;

/**
 * @Author: tron
 * @Date: 2022/1/17 5:05 PM
 */
@Mapper
public interface TransactionsMapper extends BaseMapper<Transactions> {

  /**
   * insert unconfirmed src transaction
   */
  int insertSrcTxInfo(@Param("item") Transactions transactions);

  /**
   * insert confirmed src transaction
   */
  int insertSrcTxInfoUnConfirm(@Param("item") Transactions transactions);

  List<Transactions> getUserWithdrawMessages(@Param("user") String address);

  List<Transactions> getUserTransactionRecord(@Param("user") String address);

  List<Transactions> updateTransactionCheckPoint(
      @Param("maxCheckPointBlockNum") long maxCheckPointBlockNum, @Param("chainId") int chainId);
}
