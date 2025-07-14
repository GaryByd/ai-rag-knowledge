package com.luojiawei.tech;


import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

@SpringBootApplication
@Configurable
@ComponentScan(basePackages = {
        "com.luojiawei.tech",
        "com.luojiawei.api",
        "com.luojiawei.trigger",})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

}
