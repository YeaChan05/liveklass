### Port-Adapter Pattern

현재 프로젝트는 Port-Adapter 패턴을 코드 구조뿐 아니라 모듈 구조에도 반영한다

핵심은 비즈니스 로직이 외부 기술을 직접 모르고, adapter가 port를 통해서만 core와 연결되도록 경계를 고정하는 것이다

#### 1. Inbound Port는 `service`에 둔다

유스케이스 자체가 inbound port 역할을 한다

`service`는 외부 요청을 직접 받지 않고, 수행해야 할 비즈니스 행위를 인터페이스로 먼저 드러낸다

```kotlin
fun interface {Domain}{Action}UseCase {
    fun execute(
        command: {Domain}{Action}Command,
    ): {Domain}Result
}

class {Domain}Service(
    private val {domain}Processor: {Domain}ProcessService,
    private val {domain}Writer: {Domain}Writer,
) : {Domain}{Action}UseCase
```

즉 controller나 internal adapter는 구체 service 구현이 아니라 진입 port를 호출한다

#### 2. Driving Adapter는 `api`, `api-internal`에 둔다

사용자 HTTP 요청이나 내부 HTTP 요청을 받아 inbound port로 연결하는 코드는 driving adapter다

```kotlin
@RestController
@RequestMapping("/{domains}", version = "v1")
class {Domain}Controller(
    private val {domain}{action}UseCase: {Domain}{Action}UseCase,
) : {Domain}Api
```

```kotlin
class {Domain}InternalAdapter(
    private val {domain}{action}UseCase: {Domain}{Action}UseCase,
) : {Domain}InternalApi
```

이 계층은 요청/응답을 해석하고 use case를 호출하는 역할까지만 가진다

비즈니스 규칙, 저장소 접근, 외부 시스템 연동 로직을 여기서 직접 구현하지 않는다

#### 3. Outbound Port는 `infrastructure`에 둔다

현재 프로젝트에서 `infrastructure`는 일반적인 구현 모듈이 아니라, service가 외부 경계를 바라보기 위한 outbound port를 두는 core 모듈이다

대표 형태는 repository, external client, internal client, event publisher다

```kotlin
interface {Domain}Repository {
    fun save(props: {Domain}RequestProps): {Domain}Model
    fun findById(identifier: {Domain}Identifier): {Domain}Model?
}
```

```kotlin
interface {Provider}Client {
    fun get(
        query: {Provider}Query,
    ): {Provider}Snapshot?

    fun execute(command: {Provider}Command)
}
```

이렇게 하면 `service`는 JPA, HTTP, MQ 같은 기술 세부사항 없이 port만 의존할 수 있다

#### 4. Driven Adapter는 `repository-*`, `mq-*`, consumer-side client adapter에 둔다

outbound port의 실제 구현체가 driven adapter다

JPA 저장소 구현은 `repository-jpa`에 두고, 내부 호출 client adapter는 consumer `infrastructure`에서 provider contract를 감싸는 방식으로 둔다

```kotlin
class {Domain}RepositoryImpl(
    private val repository: {Domain}JpaRepository,
) : {Domain}Repository
```

```kotlin
class {Provider}ClientAdapter(
    private val {provider}InternalApi: {Provider}InternalApi,
) : {Provider}Client {
    override fun get(
        query: {Provider}Query,
    ): {Provider}Snapshot? = {provider}InternalApi.get(
        {Provider}GetRequest(query.{provider}Id),
    )?.toSnapshot()
}
```

여기서 중요한 점은 service가 provider internal contract를 직접 보지 않는다는 것이다

service는 local port만 알고, transport 계약이나 호출 방식은 adapter가 감춘다

#### 5. 다른 도메인 내부 호출도 Port-Adapter 경계로 다룬다

다른 도메인 호출이 필요할 때도 `service -> own infrastructure port -> provider api-internal contract` 흐름을 유지한다

provider는 `api-internal.internal.contract`에 계약을 두고
consumer는 자기 `infrastructure`에서 그 계약을 감싸 local port로 변환한다

```kotlin
@HttpExchange("/internal/{domains}")
interface {Domain}InternalApi {
    @PostExchange("/query")
    fun get(
        @RequestBody request: {Domain}GetRequest,
    ): {Domain}SnapshotResponse?
}
```

```kotlin
@AutoConfiguration
class {Domain}InfrastructureBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<{Provider}Client> {
            {Provider}ClientAdapter(bean())
        }
    })
```

런타임에 same-process provider adapter를 직접 조립하든, `RestClient` 기반 HTTP client를 조립하든 consumer service는 같은 port를 사용한다

#### 6. Assembly는 adapter를 조립하지만 core 규칙을 바꾸지 않는다

`application` 같은 실행 모듈은 port와 adapter를 연결해주는 조립 지점이다

여기서 어떤 adapter를 붙일지는 바뀔 수 있지만, core가 port만 바라본다는 규칙은 바뀌지 않는다

즉 same-process 최적화가 필요하더라도 `service`가 provider bean을 직접 참조하도록 규칙을 풀지 않는다

현재 프로젝트의 runnable application은 이 점을 보여주는 예시가 된다

provider 쪽 `api-internal`은 구현체를 직접 bean으로 등록할 수 있다

```kotlin
@AutoConfiguration
class {Provider}InternalApiBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<{Provider}InternalApi> {
            {Provider}InternalAdapter(
                bean(),
            )
        }
    })
```

이렇게 하면 같은 프로세스에서 실행되는 runtime에서는 HTTP 호출 없이도 `{Provider}InternalApi` 구현체가 바로 조립될 수 있다

반대로 분리 실행 환경에서는 consumer `infrastructure`가 같은 `{Provider}InternalApi` 계약에 대해 HTTP client를 만들 수 있다

```kotlin
@AutoConfiguration
class {Consumer}InfrastructureBeanRegistrar :
    BeanRegistrarDsl({
        if (env.containsProperty("spring.http.serviceclient.{provider}-internal.base-url")) {
            registerBean<{Provider}InternalApi> {
                createInternalApiClient<{Provider}InternalApi>(
                    environment = bean(),
                    groupName = "{provider}-internal",
                )
            }
        }

        registerBean<{Provider}Client> {
            {Provider}ClientAdapter(bean())
        }
    })
```

이 두 경우 모두 `{Consumer}Service`는 `{Provider}Client`만 의존한다

즉 runtime이 바뀌어도 바뀌는 것은 assembly와 adapter wiring이고, core/service 코드는 그대로 유지된다

이 점이 현재 프로젝트에서 Port-Adapter 패턴이 주는 가장 실질적인 이점 중 하나다

#### 7. 장점

- `service`가 기술 세부사항과 transport 방식에 오염되지 않는다
- repository, internal call, MQ 같은 외부 경계를 port 단위로 교체할 수 있다
- 모듈 의존 방향을 `Driving -> Core -> Driven`으로 비교적 명확하게 유지할 수 있다
- 같은 유스케이스를 API controller, internal adapter, 테스트 코드에서 일관되게 재사용할 수 있다
- 멀티 모듈 모놀리스에서 시작해 이후 분리 가능성을 열어두기 쉽다

#### 8. 주의할 점

- `service`가 `repository-jpa`, `api-internal`, 다른 도메인 구현체를 직접 참조하면 경계가 무너진다
- consumer `service`가 provider request/response DTO를 직접 다루기 시작하면 local port의 의미가 사라진다
- adapter에서 끝나야 할 변환 로직이 `service`로 올라오면 transport 세부사항이 core에 스며든다
- `infrastructure`는 port 모듈이라는 점을 잊고 구현 의존을 과도하게 노출하면 `service -> implementation` 구조로 되돌아간다

#### 9. 정리

현재 프로젝트의 Port-Adapter 패턴은 추상적인 설계 슬로건이 아니라 모듈 규칙으로 고정되어 있다

`api`, `api-internal`은 driving adapter
`service`는 inbound port와 use case
`infrastructure`는 outbound port와 consumer-side 경계
`repository-*`, `mq-*`는 driven adapter
`application`은 assembly 역할을 맡는다

새 기능을 추가할 때도 먼저 이 위치 관계를 지키는지 확인한 뒤 코드를 배치해야 한다
