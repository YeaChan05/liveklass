## 개요

- 위치: `Core`
- 역할: 유스케이스, 트랜잭션, 오케스트레이션
- 범위: 비즈니스 흐름, 구현 도구 조합

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
- `*Reader`
- `*Writer`
- `*Processor`
- `*BeanRegistrar`

### 특징적 구현

Service는 사용자 요청이나 스케줄러 트리거에 대한 비즈니스 흐름을 표현한다.
저장소 구현 방식, Redis key, SQL 조건부 update 같은 세부사항은 Reader/Writer/Processor로 내린다.

```kotlin
interface {Domain}CreateUseCase {
    fun create(command: {Domain}CreateCommand): {Domain}Result
}

class {Domain}Service(
    private val reader: {Domain}Reader,
    private val writer: {Domain}Writer,
) : {Domain}CreateUseCase {
    override fun create(command: {Domain}CreateCommand): {Domain}Result {
        // existing right check
        // mode / policy check
        // command execution
        // fallback flow
    }
}
```

Reader는 읽기 전용 도구다. 상태 변경, 이벤트 발행, 외부 쓰기, Redis remove 같은 부수효과를 가지면 안 된다.

Writer는 상태 변경 도구다. 정합성을 위해 필요한 조회는 허용하지만, 가능하면 조회 책임은 Reader로 분리한다.

### 작명 방식

- 유스케이스: `{Domain}{Action}UseCase`
- 구현체: `{Domain}Service`
- 읽기 도구: `{Domain}Reader`, `{Domain}RepositoryReader`
- 쓰기 도구: `{Domain}Writer`, `{Domain}RepositoryWriter`
- 처리 도구: `{Name}Processor`, `{Name}Coordinator`
- 금지: `*Controller`, `*RepositoryImpl`

### 책임 기준

- `*Service`는 비즈니스 흐름을 읽히게 한다. 단순 위임만 하는 껍데기가 되어서는 안 된다.
- `*Reader`는 반드시 읽기만 한다.
- `*Writer`는 쓰기 중심 처리 도구다. 내부 정합성 확인을 위한 읽기는 허용하되, 조회 API를 노출하지 않는다.
- `*Processor`는 상태 전이, 조건부 변경, 만료 처리, 승격 처리처럼 단일 처리 단위를 맡는다.
- Scheduler는 실행 시점만 담당하고 전용 UseCase 또는 Service만 호출한다. Repository를 직접 참조하지 않는다.
- Repository는 저장소 접근만 담당한다. 비즈니스 흐름을 가지지 않는다.

## gradle.properties

- `type=kotlin-boot`
- `group={base-package}.{domain}`

## Test

- 기본: `:{domain}:service:test`
- registrar 포함 시: registrar test
- internal / transaction 경계 변경 시: `:{domain}:application:integrationTest`
