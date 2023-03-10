package org.bttc.relayer.controller;

import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.bttc.relayer.bean.common.DataResponse;
import org.bttc.relayer.bean.common.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: tron
 * @Date: 2022/10/20
 */
@Slf4j
@RestController
@RequestMapping("/status")
public class StatusController {
  @GetMapping("/info")
  @ApiOperation("get the wwork status of the relayer")
  public DataResponse<Response> info() {
    return new DataResponse<>().ok();
  }
}
