package org.bttc.relayer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.bttc.relayer.bean.dao.TokenMap;

@Mapper
public interface TokenMapMapper extends BaseMapper<TokenMap> {

    @Select("select * from token_map where chain_id = #{chainId}")
    List<TokenMap> getTokenMap(@Param("chainId") int chainId);

    @Select("select child_address from token_map")
    List<String> getAllChildAddress();

    @Select("select price from token_map where token_name = #{tokenName} limit 1")
    Double getTokenPrice(@Param("tokenName")String tokenName);

}
