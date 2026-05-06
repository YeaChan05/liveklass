## 개요

- 위치: `Core`
- 역할: 유스케이스, 트랜잭션, 오케스트레이션
- 범위: 비즈니스 규칙

## 의존 관계

### 의존 모듈

- `{domain}:model`
- `{domain}:infrastructure`
- `{domain}:exception`
- `common:security` (필요 시)

### 의존 금지 모듈

- `{domain}:api`
- `{domain}:api-internal`
- `{domain}:repository-*`
- `{domain}:mq-*`
- `{domain}:application`
- other-domain `infrastructure`
- other-domain `api-internal`

## 구현

### 구성 요소

- `*CreateUseCase`
- `*ReadUseCase`
- `*UpdateUseCase`
- `*QueryUseCase`
- `*Service`
- `*QueryService`
- `*ProcessService`
- `*BeanRegistrar`

### 특징적 구현

```kotlin
interface {Domain}CreateUseCase {
    fun create(command: {Domain}CreateCommand): {Domain}Model
}

class {Domain}Service(
    private val {domain}Repository: {Domain}Repository,
) : {Domain}CreateUseCase {
    override fun create(command: {Domain}CreateCommand): {Domain}Model {
        // validate
        // load / save
        // return
    }
}
```

### 작명 방식

- 유스케이스: `{Domain}{Action}UseCase`
- 구현체: `{Domain}Service`, `{Domain}QueryService`, `{Domain}ProcessService`
- 보조 구성요소: `{Name}Handler`, `{Name}Writer`, `{Name}Updater`
- 금지: `*Controller`, `*RepositoryImpl`

## gradle.properties

- `type=kotlin-boot`
- `group={base-package}.{domain}`

## Test

- 기본: `:{domain}:service:test`
- registrar 포함 시: registrar test
- internal / transaction 경계 변경 시: `:{domain}:application:integrationTest`
