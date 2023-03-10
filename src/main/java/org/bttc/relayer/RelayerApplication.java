package org.bttc.relayer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author tron
 */
@EnableScheduling
@MapperScan("org.bttc.relayer.bean.mapper")
@SpringBootApplication
public class RelayerApplication {

	public static void main(String[] args) {
		SpringApplication.run(RelayerApplication.class, args);
	}

}