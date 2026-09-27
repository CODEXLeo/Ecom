package com.microservice.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer // Spring Cloud mechanism for turning a normal Spring Boot application into a Eureka registry.
public class MicroserviceDiscoveryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MicroserviceDiscoveryServerApplication.class, args);
    }
}