package org.bttc.relayer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.bttc.relayer.bean.dao.MessageCenter;

/**
 * @author tron
 * @date 2021/9/17
 */
@Mapper
public interface MessageCenterMapper extends BaseMapper<MessageCenter> {

  int insertUnConfirmMsg(List<MessageCenter> list);

  int insertConfirmMsg(List<MessageCenter> list);

  @Select("select * from message_center where from_chain_id = #{fromChainId} and event_type = #{eventType} order by block_number desc limit 2")
  List<MessageCenter> getLastTwoMessage(@Param("fromChainId") String fromChainId,
      @Param("eventType") String eventType);

  @Select("select * from message_center where from_chain_id = #{fromChainId} and event_type = #{eventType} order by block_number desc limit 1")
  MessageCenter getMaxMessage(@Param("fromChainId") String fromChainId,
      @Param("eventType") String eventType);

  @Select("select * from message_center where from_chain_id = #{fromChainId} and event_type = #{eventType} order by id desc limit 1")
  MessageCenter getLatestMessage(@Param("fromChainId") String fromChainId,
      @Param("eventType") String eventType);

  @Select("select * from message_center where from_chain_id = 'tron' and token_id = #{tokenId} and event_type = #{eventType} order by id desc limit 1")
  MessageCenter getLatestRewardMessage(@Param("tokenId") String tokenId,
      @Param("eventType") String eventType);

  @Select("select * from message_center where tx_id = #{txId} and from_chain_id = #{fromChainId} and event_type = #{eventType} limit 1")
  MessageCenter getMessageByTxIdAndType(@Param("fromChainId") String fromChainId,
      @Param("txId") String txId, @Param("eventType") String eventType);

  @Select("select id,tx_id,event_type,content,time_stamp from message_center where from_chain_id = #{fromChainId} and event_type = #{eventType} and id > #{from} and id <= #{to}")
  List<MessageCenter> getTxIdsFromId(@Param("fromChainId") String fromChainId,
      @Param("eventType") String eventType, @Param("from") Long from, @Param("to") Long to);

  @Select("select id,tx_id,event_type,from_chain_id,content,time_stamp from message_center where from_chain_id = 'tron' and token_id = #{tokenId} and event_type = #{eventType} and id > #{from} and id <= #{to}")
  List<MessageCenter> getRewardTxIdsFromId(@Param("tokenId") String tokenId,
      @Param("eventType") String eventType, @Param("from") Long from, @Param("to") Long to);

  @Select("select id,tx_id,event_type,content,time_stamp from message_center where from_chain_id = #{fromChainId} and event_type in ('DelegatorClaimedRewards','ClaimRewards','DelegatorRestaked') and id > #{from} and id <= #{to}")
  List<MessageCenter> queryStackInfoTxIds(@Param("fromChainId") String fromChainId,
      @Param("from") Long from, @Param("to") Long to);

  @Select("select * from message_center where from_chain_id = #{fromChainId} and event_type in ('DelegatorClaimedRewards','ClaimRewards','DelegatorRestaked') order by id desc limit 1")
  MessageCenter getLatestStackInfoMessage(@Param("fromChainId") String fromChainId);

}
