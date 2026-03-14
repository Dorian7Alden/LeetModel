package com.senior.leetmodelbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@SpringBootApplication
@ServletComponentScan
public class LeetModelBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeetModelBackendApplication.class, args);
    }

}
