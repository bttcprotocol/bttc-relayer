package org.bttc.relayer.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bttc.relayer.bean.dao.EventUrlConfig;
import org.bttc.relayer.mapper.EventUrlConfigMapper;
import org.springframework.stereotype.Service;

/**
 * @author tron
 * @date 2021/9/24 9:02
 */
@Slf4j
@Service
public class EventUrlConfigService {

    @Resource
    private EventUrlConfigMapper eventUrlConfigMapper;

    public List<String> queryUrlConfig(String chain) {
        List<String> urlList = new ArrayList<>();
        Map<String, Object> columnMap = new HashMap<>(1);
        columnMap.put("chain", chain);
        List<EventUrlConfig> configList = eventUrlConfigMapper.selectByMap(columnMap);
        for (EventUrlConfig config : configList) {
            urlList.add(config.getUrl());
        }
        return urlList;
    }

}
