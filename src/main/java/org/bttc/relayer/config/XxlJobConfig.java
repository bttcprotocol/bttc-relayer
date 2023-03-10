package org.bttc.relayer.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: tron
 * @Date: 2021/12/23 6:48 PM
 */
@Configuration
@Slf4j
public class XxlJobConfig {

  @Value("${xxl.job.admin.addresses}")
  private String adminAddresses;

  @Value("${xxl.job.accessToken}")
  private String accessToken;

  @Value("${xxl.job.executor.appname}")
  private String appname;

  @Value("${xxl.job.executor.address}")
  private String address;

  @Value("${xxl.job.executor.ip}")
  private String ip;

  @Value("${xxl.job.executor.port}")
  private int port;

  @Value("${xxl.job.executor.logpath}")
  private String logPath;

  @Value("${xxl.job.executor.logretentiondays}")
  private int logRetentionDays;


  @Bean
  public XxlJobSpringExecutor xxlJobExecutor() {
    log.info(">>>>>>>>>>> xxl-job config init.");
    XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
    xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
    xxlJobSpringExecutor.setAppname(appname);
    xxlJobSpringExecutor.setAddress(address);
    xxlJobSpringExecutor.setIp(ip);
    xxlJobSpringExecutor.setPort(port);
    xxlJobSpringExecutor.setAccessToken(accessToken);
    xxlJobSpringExecutor.setLogPath(logPath);
    xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);
    log.info(">>>>>>>>>>> xxl-job config init success. adminAddress = [{}], appName = [{}]",
        adminAddresses, appname);
    return xxlJobSpringExecutor;
  }

  /**
   * For situations such as multiple network cards and deployment in containers,
   * you can flexibly customize the registration IP with the help of
   * the "InetUtils" component provided by "spring-cloud-commons";
   *
   *      1、import dependency：
   *          <dependency>
   *             <groupId>org.springframework.cloud</groupId>
   *             <artifactId>spring-cloud-commons</artifactId>
   *             <version>${version}</version>
   *         </dependency>
   *
   *      2、Configuration files, or container startup variables
   *          spring.cloud.inetutils.preferred-networks: 'xxx.xxx.xxx.'
   *
   *      3、get IP
   *          String ip_ = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
   */
}

