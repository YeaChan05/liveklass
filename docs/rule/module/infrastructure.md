## 개요

- 위치: `Core`
- 역할: out-port, internal client 경계, consumer-side 변환
- 범위: 구현체 비의존, 경계 정의

## 의존 관계

### 의존 모듈

- `{domain}:model`
- other-domain `api-internal` (internal consumer 시)
- `common:security` (필요 시)
- `spring-web` (필요 시)

### 의존 금지 모듈

- `{domain}:repository-*`
- `{domain}:mq-*`
- `{domain}:api`
- `{domain}:application`
- other-domain `repository-*`
- other-domain `mq-*`

## 구현

### 구성 요소

- `*Repository`
- `*ExternalClient`
- `*EventPublisher`
- `*Client`
- `*ClientAdapter`
- `*Snapshot`
- `*Command`
- `*Query`

### 특징적 구현

```kotlin
interface {Domain}Repository {
    fun save(model: {Domain}Model): {Domain}Model
    fun get(identifier: {Domain}Identifier): {Domain}Model?
}

interface {Provider}Client {
    fun get(query: {Provider}Query): {Provider}Snapshot
}
```

### 작명 방식

- 포트: `{Domain}Repository`, `{Domain}ExternalClient`, `{Domain}EventPublisher`
- internal client: `{Domain}Client`, `{Domain}ClientAdapter`
- transfer-local type: `{Domain}Snapshot`, `{Domain}Command`, `{Domain}Query`
- 노출 원칙: `model -> api(...)`, provider contract -> `implementation(...)`

## gradle.properties

- `type=kotlin-boot`
- `group={base-package}.{domain}`

## Test

- 기본: `:{domain}:service:test`
- 필요 시: `:{domain}:infrastructure:test`
- internal 경계 변경 시: `:{domain}:application:integrationTest`
