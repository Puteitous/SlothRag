package com.ragbase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ragbase 启动类
 */
@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class RagbaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagbaseApplication.class, args);
    }
}
