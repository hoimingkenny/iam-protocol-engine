package com.iam.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.iam",
    exclude = {RedisAutoConfiguration.class})
@EntityScan(basePackages = "com.iam.authcore.entity")
@EnableJpaRepositories(basePackages = "com.iam.authcore.repository")
public class ApiGatewayTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayTestApplication.class, args);
    }
}
