package org.bttc.relayer.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Throwables;
import java.security.SecureRandom;
import java.util.Map.Entry;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.http.*;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author tron
 * @date 2021/9/16 2:11
 */
@Slf4j
public class HttpClientUtil {

  private HttpClientUtil() {

  }
  private static CloseableHttpClient httpclient = null;
  public static final String UTF_8 = "utf-8";
  static final int CONNECTION_REQUEST_TIMEOUT = 20000;
  static final int CONNECT_TIMEOUT = 20000;
  static final int SOCKET_TIMEOUT = 60000;

  static final int MAX_TOTAL = 500;
  static final int MAX_PER_ROUTE = 100;

  static final String DETAIL_HOST_NAME = "http://www.baidu.com";
  static final int DETAIL_PORT = 80;
  static final int DETAIL_MAX_PER_ROUTE = 100;

  private static SecureRandom rand = new SecureRandom();

  private static CloseableHttpClient getHttpClient() {
    if (null == httpclient) {
      synchronized (HttpClientUtil.class) {
        httpclient = init();
      }
    }
    return httpclient;
  }

  /**
   * Connection pool initialization.
   * The most important point of understanding here is to keep CloseableHttpClient alive in the world of the pool,
   * but HttpPost is always used up and disappears.This keeps the link alive.
   */
  private static CloseableHttpClient init() {
    CloseableHttpClient newHttpclient;

    // set connection pool
    ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
    LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();
    Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create().register("http", plainsf).register("https", sslsf).build();
    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
    cm.setMaxTotal(MAX_TOTAL);
    cm.setDefaultMaxPerRoute(MAX_PER_ROUTE);

    // Start to refine the configuration.
    // In fact, use the for loop of Map or List to configure each link here, which is very useful in special scenarios.
    // Specialize the connection of each routing base, generally not needed
    HttpHost httpHost = new HttpHost(DETAIL_HOST_NAME, DETAIL_PORT);
    // Increase the maximum number of connections to the target host
    cm.setMaxPerRoute(new HttpRoute(httpHost), DETAIL_MAX_PER_ROUTE);
    // End of detailed configuration

    // request retry processing
    HttpRequestRetryHandler httpRequestRetryHandler = (exception, executionCount, context) -> {
      if (executionCount >= 2) {
        return false;
      }
      if (exception instanceof NoHttpResponseException) {
        return true;
      }
      if (exception instanceof SSLHandshakeException) {
        return false;
      }
      if (exception instanceof InterruptedIOException) {
        return false;
      }
      if (exception instanceof UnknownHostException) {
        return false;
      }
      if (exception instanceof SSLException) {
        return false;
      }

      HttpClientContext clientContext = HttpClientContext.adapt(context);
      HttpRequest request = clientContext.getRequest();
      return !(request instanceof HttpEntityEnclosingRequest);
    };

    RequestConfig requestConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).setConnectionRequestTimeout(
        CONNECTION_REQUEST_TIMEOUT).setConnectTimeout(CONNECT_TIMEOUT).setSocketTimeout(
        SOCKET_TIMEOUT).build();
    newHttpclient = HttpClients.custom().setConnectionManager(cm).setDefaultRequestConfig(requestConfig).setRetryHandler(httpRequestRetryHandler).build();
    return newHttpclient;
  }

  public static String doGet(String url, Map<String, String> param) {

    CloseableHttpClient httpclient = getHttpClient();

    String resultString = "";
    CloseableHttpResponse response = null;
    try {
      // create uri
      URIBuilder builder = new URIBuilder(url);
      if (param != null) {
        for (Entry<String, String> entry : param.entrySet()) {
          builder.addParameter(entry.getKey(), entry.getValue());
        }
      }
      URI uri = builder.build();

      HttpGet httpGet = new HttpGet(uri);

      response = httpclient.execute(httpGet);
      if (response.getStatusLine().getStatusCode() == 200) {
        resultString = EntityUtils.toString(response.getEntity(), "UTF-8");
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if(null != response){
          response.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return resultString;
  }

  public static String doPost(String url, Map<String, String> param) {
    CloseableHttpClient httpClient = getHttpClient();
    CloseableHttpResponse response = null;
    String resultString = "";
    try {
      HttpPost httpPost = new HttpPost(url);
      if (param != null) {
        List<NameValuePair> paramList = new ArrayList<>();
        for (Entry<String, String> entry : param.entrySet()) {
          paramList.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(paramList, UTF_8);
        httpPost.setEntity(entity);
      }
      response = httpClient.execute(httpPost);
      resultString = EntityUtils.toString(response.getEntity(), UTF_8);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (response != null) {
          response.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return resultString;
  }

  public static String doPostJson(String url, String json) {
    CloseableHttpClient httpClient = getHttpClient();
    CloseableHttpResponse response = null;
    String resultString = "";
    try {
      HttpPost httpPost = new HttpPost(url);
      StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
      httpPost.setEntity(entity);
      response = httpClient.execute(httpPost);
      resultString = EntityUtils.toString(response.getEntity(), UTF_8);
    } catch (Exception e) {
      log.warn("do Post Json error, exception is [{}]",
          Throwables.getStackTraceAsString(e));
    } finally {
      try {
        if(null != response){
          response.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return resultString;
  }
  //Only applicable when the returned data is JSON
  public static String doPostJsonRetry(String url, String json, int retryTimes) {
    retryTimes--;
    if(retryTimes <= 0) {
      return doPostJson(url, json);
    }
    String resultString = doPostJson(url, json);
    JSONObject jsonResult = new JSONObject();
    try {
      jsonResult = JSON.parseObject(resultString);
      if (jsonResult.containsKey("error")) {
        log.warn("doPostJsonRetry error! the url: {}, post json: {}, error: {}",
            url, json, jsonResult);
      } else {
        return resultString;
      }
    } catch (Exception e) {
      log.warn("doPostJsonRetry error! the url: {}, post json: {}, exception is : {}",
          url, json, Throwables.getStackTraceAsString(e));
    }
    try {
      // sleep for a random time in case of centralized parallel retries
      int sleepTime = rand.nextInt(500) + 500;
      Thread.sleep(sleepTime);
    } catch (Exception e){
      log.warn("EthClient postReTry sleep exception! exception: {}",
          Throwables.getStackTraceAsString(e));
      Thread.currentThread().interrupt();
    }
    return doPostJsonRetry(url, json, retryTimes);
  }

  public static String doPostJsonWithApiKey(String url, String json, String apiKeyName, String apiKey) {
    CloseableHttpClient httpClient = getHttpClient();
    CloseableHttpResponse response = null;
    String resultString = "";
    try {
      HttpPost httpPost = new HttpPost(url);
      httpPost.setHeader(apiKeyName, apiKey);
      StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
      httpPost.setEntity(entity);
      response = httpClient.execute(httpPost);
      resultString = EntityUtils.toString(response.getEntity(), UTF_8);
    } catch (Exception e) {
      log.warn("do Post Json With ApiKey error, exception is [{}]",
          Throwables.getStackTraceAsString(e));
    } finally {
      try {
        if(null != response){
          response.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return resultString;
  }
  //Only applicable when the returned data is JSON
  public static String doPostJsonWithApiKeyRetry(String url, String json, String apiKeyName, String apiKey, int retryTimes) {
    retryTimes--;
    if(retryTimes <= 0) {
      return doPostJsonWithApiKey(url, json, apiKeyName, apiKey);
    }
    String resultString = doPostJsonWithApiKey(url, json, apiKeyName, apiKey);
    try {
      JSONObject jsonResult = JSON.parseObject(resultString);
      if (MapUtils.isEmpty(jsonResult)) {
        log.warn("doPostJsonWithApiKeyRetry returns null! the url: {}, post json: {}, error: {}",
            url, json, jsonResult);
      } else if (jsonResult.containsKey("error")) {
        log.warn("doPostJsonWithApiKeyRetry error! the url: {}, post json: {}, error: {}",
            url, json, jsonResult);
      } else {
        return resultString;
      }
    } catch (Exception e) {
      log.warn("doPostJsonWithApiKeyRetry error! the url: {}, post json: {}, exception is : {}",
          url, json, Throwables.getStackTraceAsString(e));
    }
    try {
      // sleep for a random time in case of centralized parallel retries
      int sleepTime = rand.nextInt(500) + 500;
      Thread.sleep(sleepTime);
    } catch (Exception e){
      log.warn("EthClient postReTry sleep exception! exception: {}",
          Throwables.getStackTraceAsString(e));
      Thread.currentThread().interrupt();
    }
    return doPostJsonWithApiKeyRetry(url, json, apiKeyName, apiKey, retryTimes);
  }


  public static String doPostJsonUnCatch(String url, String json) throws IOException {
    CloseableHttpClient httpClient = getHttpClient();
    CloseableHttpResponse response = null;
    String resultString = "";
    try {
      HttpPost httpPost = new HttpPost(url);
      StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
      httpPost.setEntity(entity);
      response = httpClient.execute(httpPost);
      resultString = EntityUtils.toString(response.getEntity(), UTF_8);
    }  finally {
      try {
        if(null != response){
          response.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return resultString;
  }

  public static String getByApiKey(String url, String apiKeyName, String apiKey) {
    CloseableHttpClient httpclient = getHttpClient();

    String resultString = "";
    CloseableHttpResponse response = null;
    try {
      // create uri
      URIBuilder builder = new URIBuilder(url);
      URI uri = builder.build();

      HttpGet httpGet = new HttpGet(uri);
      httpGet.setHeader(apiKeyName, apiKey);

      response = httpclient.execute(httpGet);
      if (response.getStatusLine().getStatusCode() == 200) {
        resultString = EntityUtils.toString(response.getEntity(), "UTF-8");
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if(null != response){
          response.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return resultString;
  }
}

