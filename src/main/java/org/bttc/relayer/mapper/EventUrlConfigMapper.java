package org.bttc.relayer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.bttc.relayer.bean.dao.EventUrlConfig;

/**
 * @author tron
 * @date 2021/9/29
 */
@Mapper
public interface EventUrlConfigMapper extends BaseMapper<EventUrlConfig> {
}
