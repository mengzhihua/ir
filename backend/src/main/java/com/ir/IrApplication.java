package com.ir;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.ir.**.mapper")
public class IrApplication {
    public static void main(String[] args) {
        SpringApplication.run(IrApplication.class, args);
    }
}
