## 개요

- 위치: `Core`
- 역할: 비즈니스 예외
- 범위: 도메인 의미

## 의존 관계

### 의존 모듈

- `common:exception`

### 의존 금지 모듈

- Spring Web 예외
- DB 예외
- MQ 예외
- `RuntimeException` 직접 상속

## 구현

### 구성 요소

- `*Exception`
- `*NotFoundException`
- `*DuplicateException`
- `*PermissionDeniedException`
- `*InvalidStateException`

### 특징적 구현

```kotlin
open class {Domain}Exception(
    message: String,
) : BusinessException(message)

class {Domain}NotFoundException(identifier: Long) :
    {Domain}Exception("{Domain} not found. id=$identifier")
```

### 작명 방식

- 기준: 도메인 의미
- 상속: `BusinessException`
- 금지: 기술 타입명 직접 노출

## gradle.properties

- `type=kotlin`
- `group={base-package}.{domain}`

## Test

- 기본: 상위 `service` / `api` 테스트 간접 보호
- 필요 시: `:{domain}:exception:test`
