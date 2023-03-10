package org.bttc.relayer.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bttc.relayer.constant.CommonConstant;
import org.bttc.relayer.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author tron
 * @date 2021/9/17 6:29
 */
@Slf4j
@Component
public class BttcClient {

  @Value("${client.bttc.url:http://47.253.46.247:8545}")
  private String bttcUrl;

  public JSONObject getBlockByNumber(String blockNum) {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(CommonConstant.JSON_RPC, "2.0");
    jsonObject.put(CommonConstant.METHOD, "eth_getBlockByNumber");
    JSONArray jsonArray = new JSONArray();
    jsonArray.add(blockNum);
    jsonArray.add(false);
    jsonObject.put(CommonConstant.PARAMS, jsonArray);
    jsonObject.put("id", "1");
    String postResult = HttpClientUtil.doPostJsonRetry(bttcUrl, jsonObject.toJSONString(),
        CommonConstant.CONTROLLER_RETRY_TIMES);
    if (StringUtils.isBlank(postResult)) {
      return new JSONObject();
    }
    return JSON.parseObject(postResult);
  }

}
