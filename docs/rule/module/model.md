## 개요

- 위치: `Core`
- 역할: 도메인 상태, 식별자, 값 객체, 계산 결과
- 범위: 기술 비의존

## 의존 관계

### 의존 모듈

- 없음
- other-domain `model` (필요 시)

### 의존 금지 모듈

- `{domain}:service`
- `{domain}:infrastructure`
- `{domain}:api`
- `{domain}:api-internal`
- `{domain}:repository-*`
- `{domain}:mq-*`
- `{domain}:application`
- Spring
- JPA
- Web
- MQ

## 구현

### 구성 요소

- `*Model`
- `*Identifier`
- `*Props`
- `*Value`
- `*Policy`

### 특징적 구현

```kotlin
interface {Domain}Identifier {
    val {domain}Id: Long?
}

interface {Domain}Model :
    {Domain}Props,
    {Domain}Identifier
```

### 작명 방식

- Aggregate Root: `{Domain}Model`
- 식별자: `{Domain}Identifier`
- 상태 묶음: `{Domain}Props`
- 값 / 정책: `{Name}Value`, `{Name}Policy`
- 금지: `*Entity`, `*Dto`, `*Vo`

## gradle.properties

- `type=kotlin`
- `group={base-package}.{domain}`

## Test

- 기본: 상위 `service` 테스트 간접 보호
- 필요 시: `:{domain}:model:test`
