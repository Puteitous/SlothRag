package com.slothrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * slothrag 启动类
 */
@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class SlothRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlothRagApplication.class, args);
    }
}