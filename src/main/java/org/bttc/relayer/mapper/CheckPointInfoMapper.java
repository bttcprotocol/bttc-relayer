package org.bttc.relayer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.bttc.relayer.bean.dao.CheckPointInfo;

@Mapper
public interface CheckPointInfoMapper extends BaseMapper<CheckPointInfo> {

  int insertUnConfirmMsg(List<CheckPointInfo> list);

  int insertConfirmMsg(List<CheckPointInfo> list);

  @Select("select * from check_point_info where chain_id = #{chainId} order by check_point_num desc limit 1")
  CheckPointInfo getChainMaxCheckPointMessage(@Param("chainId") int chainId);

  @Select("select * from check_point_info  order by id desc limit 1")
  CheckPointInfo getLatestCheckPointMessage();

  @Select("select * from check_point_info where id > #{from} and id <= #{to}")
  List<CheckPointInfo> getCheckPointTxIdsFromId(@Param("from") Long from, @Param("to") Long to);

  @Select("select * from check_point_info where chain_id = #{chainId} order by check_point_num desc limit 2")
  List<CheckPointInfo> getLastTwoMessage(@Param("chainId") int chainId);

  @Select("select check_point_num from check_point_info where chain_id = #{chainId} order by check_point_num desc limit 1")
  long getMaxCheckPointNumber(@Param("chainId") int chainId);
}
