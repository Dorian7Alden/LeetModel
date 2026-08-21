package com.senior.leetmodelbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ServletComponentScan
@EnableAsync
public class LeetModelBackendApplication {

    public static void main(String[] args) {
        System.setProperty("pagehelper.banner", "false");   // PageHelper 2.x 版本只能通过 System.setProperty() 禁用 banner
        SpringApplication.run(LeetModelBackendApplication.class, args);
    }

}
