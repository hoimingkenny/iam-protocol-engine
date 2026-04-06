package com.iam.authcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.iam.authcore.entity")
@EnableJpaRepositories(basePackages = "com.iam.authcore.repository")
public class AuthCoreTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthCoreTestApplication.class, args);
    }
}
