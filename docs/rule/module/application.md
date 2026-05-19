## 개요

- 위치: `Assembly`
- 역할: 실행 entrypoint, bean 조립, integration test 소유
- 범위: runtime assembly

## 의존 관계

### 의존 모듈

- `common:security`
- `{domain}:api`
- `{domain}:api-internal` (필요 시)
- `{domain}:repository-jpa`
- `{domain}:repository-redis` (필요 시)
- `{domain}:schema`

### 의존 금지 모듈

- other-domain `service`
- other-domain `infrastructure`
- domain 경계 우회 조립
- business logic 소유

## 구현

### 구성 요소

- `Application`
- `*ApplicationSecurityBeanRegistrar`
- `application.yml`
- integration test fixture
- integration test spec

### 특징적 구현

```kotlin
@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
```

### 작명 방식

- entrypoint: `Application`
- registrar: `{Domain}ApplicationSecurityBeanRegistrar`
- fixture: `{Domain}TestFixtures`, `{Domain}TestFixturesConfig`

## gradle.properties

- `type=kotlin-boot-application`
- `group={base-package}.{domain}`

## Test

- 기본: `:{domain}:application:integrationTest`
- registrar test
- API spec test
