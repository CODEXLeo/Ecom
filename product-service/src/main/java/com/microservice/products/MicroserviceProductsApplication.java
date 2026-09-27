package com.microservice.products;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication
public class MicroserviceProductsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroserviceProductsApplication.class, args);
    }
}