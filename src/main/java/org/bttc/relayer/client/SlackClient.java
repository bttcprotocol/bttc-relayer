package org.bttc.relayer.client;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bttc.relayer.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SlackClient {
  @Value("${setting.project:bttc-relayer}")
  private String project;

  @Value("${setting.env:pretest}")
  private String env;

  private static final int RE_TRY_TIMES = 3;

  @Value("${client.slack.url:https://hooks}")
  private String slackUrl;

  public boolean sendSlackMessage(String msg) {
    msg = "[" + project + "-" + env + "] " + msg;
    return this.sendTextMessage(slackUrl, msg);
  }

  public boolean sendTextMessage(String msg) {
    JSONObject jsonObject = new JSONObject();
    msg = "[" + project + "-" + env + "] " + msg;
    jsonObject.put("text", msg);
    String doGetResult = postReTry(jsonObject.toJSONString(), RE_TRY_TIMES);
    return StringUtils.isBlank(doGetResult);
  }

  public boolean sendTextMessage(String url, String msg) {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("text", msg);
    String doGetResult = postReTry(url, jsonObject.toJSONString(), RE_TRY_TIMES);
    return StringUtils.isBlank(doGetResult);
  }


  private String postReTry(String json, Integer retryTimes) {
    return postReTry(slackUrl, json, retryTimes);
  }

  private String postReTry(String url, String json, Integer retryTimes) {
    if (retryTimes <= 0) {
      return "";
    }
    try {
      return HttpClientUtil.doPostJsonUnCatch(url, json);
    } catch (Exception ex) {
      String stackTraceStr = Throwables.getStackTraceAsString(ex);
      if (stackTraceStr.contains("Timeout")) {
        retryTimes--;
        return postReTry(json, retryTimes);
      } else {
        log.error("SlackClient postReTry fail : {}", stackTraceStr);
        return "";
      }
    }
  }

}


