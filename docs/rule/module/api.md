## 개요

- 위치: `Driving`
- 역할: 외부 HTTP endpoint, request/response DTO, web helper, registrar
- 범위: transport

## 의존 관계

### 의존 모듈

- `common:api`
- `{domain}:service`
- `{domain}:exception`

### 의존 금지 모듈

- `{domain}:model` 직접 응답 노출
- `{domain}:repository-*`
- `{domain}:mq-*`
- other-domain `api`
- other-domain `api-internal`
- other-domain `application`

## 구현

### 구성 요소

- `*Controller`
- `*Request`
- `*Response`
- `*Handler`
- `*Registry`
- `*Adapter`
- `*ApiBeanRegistrar`

### 특징적 구현

```kotlin
@RestController
class {Domain}Controller(
    private val createUseCase: {Domain}CreateUseCase,
) {
    @PostMapping("/{domains}")
    fun create(
        @RequestBody request: {Domain}CreateRequest,
    ): {Domain}CreateResponse {
        val result = createUseCase.create(request.toCommand())
        return {Domain}CreateResponse.from(result)
    }
}
```

### 작명 방식

- Controller: `{Domain}Controller`, `{Domain}ApiController`
- DTO: `{Domain}{Action}Request`, `{Domain}{Action}Response`
- web helper: `{Domain}{Action}Handler`, `{Domain}{Action}Registry`, `{Domain}{Action}Adapter`
- registrar: `{Domain}ApiBeanRegistrar`
- 금지: web helper `*UseCase`

## gradle.properties

- `type=kotlin-boot-mvc`
- `group={base-package}.{domain}`

## Test

- 기본: `:{domain}:api:test`
- controller test
- registrar test
