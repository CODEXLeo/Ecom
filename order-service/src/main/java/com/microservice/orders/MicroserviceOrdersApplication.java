package com.microservice.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class MicroserviceOrdersApplication {
	public static void main(String[] args) {
		SpringApplication.run(MicroserviceOrdersApplication.class, args);
	}

}
