---
title: The API Gateway Entry Point
sidebar_position: 5
description: How ApiGatewayApplication.java bootstraps all 8 modules with a single @SpringBootApplication annotation and correct component scanning.
---

# The API Gateway Entry Point

## What Was Built

`ApiGatewayApplication.java` — the single `@SpringBootApplication` that wires all 8 modules together.

```java
@SpringBootApplication(scanBasePackages = "com.iam")
@EntityScan(basePackages = "com.iam.authcore.entity")
@EnableJpaRepositories(basePackages = "com.iam.authcore.repository")
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

## Component Scanning

All modules live under `com.iam.*`:

```
com.iam.authcore
com.iam.oauth-oidc
com.iam.saml-federation
com.iam.scim
com.iam.mfa
com.iam.device-flow
com.iam.demo-resource
```

`scanBasePackages = "com.iam"` picks up all `@Component`, `@Service`, `@Controller` beans across all modules without listing each package explicitly.

## JPA Scanning

Entities and repositories are only in `auth-core`. The `@EntityScan` and `@EnableJpaRepositories` annotations are scoped to `com.iam.authcore.entity` and `com.iam.authcore.repository` respectively — they don't need to scan broadly.

## Configuration

```yaml
# backend/api-gateway/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/iam_engine
    username: iam_user
    password: ${POSTGRES_PASSWORD}  # resolved from infra/.env
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD}   # resolved from infra/.env

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

## Running

```bash
# Start Docker infrastructure first
docker compose -f infra/docker-compose.yml up -d

# Apply database schema (once, after Docker is up)
docker exec -i iam-postgres psql -U iam_user -d iam_engine \
  < backend/auth-core/src/main/resources/db/migration/V1__init.sql

# Start the application
./mvnw spring-boot:run -pl backend/api-gateway
# Starts on http://localhost:8080
```

## Health Check

```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"} with db=UP and redis=UP
```

## Test Configuration

Tests use H2 in-memory database instead of PostgreSQL:

```yaml
# backend/api-gateway/src/test/resources/application.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
```

Redis is mocked in tests via `@MockBean` since it's not available in the test environment.
