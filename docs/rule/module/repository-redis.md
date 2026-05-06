## 개요

- 위치: `Driven`
- 역할: Redis persistence/cache adapter, Redis 자료구조 추상화, Lua script 실행, auto-configuration
- 범위: Redis 기반 저장소 구현

## 의존 관계

### 의존 모듈

- `common:repository-redis`
- `{domain}:infrastructure`
- Spring Data Redis
- Redis Client
  - 기본: Lettuce
  - 필요 시: Redisson

### 의존 금지 모듈

- `{domain}:service`
- `{domain}:api`
- `{domain}:api-internal`
- `{domain}:application`
- other-domain `repository-*`
- `{domain}:repository-jpa`

## 구현

### 구성 요소

- `*RepositoryImpl`
- `*RedisRepository`
- `*RedisKey`
- `*RedisValue`
- `*RedisScript`
- `*RepositoryAutoConfiguration`

### 특징적 구현

Redis 접근 구현체는 `RedisTemplate`, `StringRedisTemplate`, `RedisScript`를 직접 외부 계층으로 노출하지 않는다.

서비스 계층에서는 Redis 자료구조나 명령어를 알지 못해야 하며, `{domain}:infrastructure`에 정의된 port를 통해서만 접근한다.

```kotlin
interface {Domain}Repository {
    fun save(value: {Domain})
    fun findById(id: {Domain}Id): {Domain}?
    fun deleteById(id: {Domain}Id)
}
````

```kotlin
class {Domain}RepositoryImpl(
    private val {domain}RedisRepository: {Domain}RedisRepository,
) : {Domain}Repository {

    override fun save(value: {Domain}) {
        {domain}RedisRepository.save(value)
    }

    override fun findById(id: {Domain}Id): {Domain}? {
        return {domain}RedisRepository.findById(id)
    }

    override fun deleteById(id: {Domain}Id) {
        {domain}RedisRepository.deleteById(id)
    }
}
```

### Redis Key

Redis key는 단순 문자열로 직접 다루지 않고, 타입으로 감싼다.

```kotlin
sealed interface RedisKey {
    val value: String
}
```

```kotlin
data class EntityRedisKey(
    private val namespace: String,
    private val id: String,
) : RedisKey {

    override val value: String = "$namespace:$id"
}
```

key 작성 규칙은 다음을 따른다.

- `{namespace}:{identifier}`
- `{namespace}:{usecase}:{identifier}`
- service 계층에서 Redis key 문자열 직접 생성 금지
- key prefix는 `RedisKey` 구현체 또는 repository 내부에서만 관리
- TTL이 필요한 key는 repository method 또는 key 정책 객체에서 명시

### Redis Value

Redis value는 raw string, json, hash map이 외부 계층으로 새어나가지 않도록 저장 전용 값 객체로 감싼다.

```kotlin
data class EntityRedisValue(
    val id: String,
    val payload: String,
    val createdAt: String,
)
```

도메인 객체와 Redis 저장 객체는 분리한다.

```kotlin
data class EntityRedisValue(
    val id: String,
    val payload: String,
    val createdAt: String,
) {
    fun toModel(): {Domain} {
        return {Domain}(
            id = {Domain}Id(id),
            payload = payload,
        )
    }

    companion object {
        fun from(model: {Domain}): EntityRedisValue {
            return EntityRedisValue(
                id = model.id.value,
                payload = model.payload,
                createdAt = model.createdAt.toString(),
            )
        }
    }
}
```

### Redis Repository

Redis 자료구조별 연산은 repository 내부에 숨긴다.

```kotlin
class {Domain}RedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun save(value: {Domain}) {
        val key = EntityRedisKey(
            namespace = "{domain}",
            id = value.id.value,
        )

        val redisValue = EntityRedisValue.from(value)

        redisTemplate.opsForValue().set(
            key.value,
            objectMapper.writeValueAsString(redisValue),
            Duration.ofMinutes(10),
        )
    }

    fun findById(id: {Domain}Id): {Domain}? {
        val key = EntityRedisKey(
            namespace = "{domain}",
            id = id.value,
        )

        val json = redisTemplate.opsForValue().get(key.value)
            ?: return null

        return objectMapper.readValue(json, EntityRedisValue::class.java)
            .toModel()
    }

    fun deleteById(id: {Domain}Id) {
        val key = EntityRedisKey(
            namespace = "{domain}",
            id = id.value,
        )

        redisTemplate.delete(key.value)
    }
}
```

### Redis 자료구조 추상화

Redis 자료구조는 외부로 직접 노출하지 않는다.

예를 들어 ZSET을 사용하는 경우에도 `ZSetOperations`를 외부 계층에 노출하지 않고, 저장소 내부에서만 사용한다.

```kotlin
class SortedSetRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {

    fun add(
        key: RedisKey,
        member: String,
        score: Double,
    ) {
        redisTemplate.opsForZSet()
            .add(key.value, member, score)
    }

    fun incrementScore(
        key: RedisKey,
        member: String,
        delta: Double,
    ) {
        redisTemplate.opsForZSet()
            .incrementScore(key.value, member, delta)
    }

    fun findTop(
        key: RedisKey,
        limit: Long,
    ): List<SortedSetEntry> {
        return redisTemplate.opsForZSet()
            .reverseRangeWithScores(key.value, 0, limit - 1)
            ?.mapNotNull { tuple ->
                val member = tuple.value ?: return@mapNotNull null
                val score = tuple.score ?: return@mapNotNull null

                SortedSetEntry(
                    member = member,
                    score = score,
                )
            }
            ?: emptyList()
    }

    fun remove(
        key: RedisKey,
        member: String,
    ) {
        redisTemplate.opsForZSet()
            .remove(key.value, member)
    }
}
```

```kotlin
data class SortedSetEntry(
    val member: String,
    val score: Double,
)
```

Hash 자료구조도 동일하게 감싼다.

```kotlin
class HashRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {

    fun put(
        key: RedisKey,
        field: String,
        value: String,
    ) {
        redisTemplate.opsForHash<String, String>()
            .put(key.value, field, value)
    }

    fun get(
        key: RedisKey,
        field: String,
    ): String? {
        return redisTemplate.opsForHash<String, String>()
            .get(key.value, field)
    }

    fun entries(key: RedisKey): Map<String, String> {
        return redisTemplate.opsForHash<String, String>()
            .entries(key.value)
    }

    fun delete(
        key: RedisKey,
        field: String,
    ) {
        redisTemplate.opsForHash<String, String>()
            .delete(key.value, field)
    }
}
```

### Lua Script

Lua script는 문자열을 service 계층에서 직접 실행하지 않는다.

각 script는 하나의 객체로 캡슐화한다.

```kotlin
class SetIfAbsentScript(
    private val redisTemplate: StringRedisTemplate,
) {

    private val script = RedisScript.of(
        """
        local current = redis.call('get', KEYS[1])

        if current ~= false then
            return 0
        end

        redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2])
        return 1
        """.trimIndent(),
        Long::class.java,
    )

    fun execute(
        key: RedisKey,
        value: String,
        ttl: Duration,
    ): Boolean {
        val result = redisTemplate.execute(
            script,
            listOf(key.value),
            value,
            ttl.toMillis().toString(),
        )

        return result == 1L
    }
}
```

Lua script 사용 규칙은 다음을 따른다.

- script는 `*Script` 클래스로 캡슐화
- 입력 key는 `RedisKey` 타입으로 전달
- 반환값은 Long, Boolean, Enum, sealed class 등으로 변환
- service 계층에 Lua 반환 코드 노출 금지
- script 본문은 가능하면 `src/main/resources/redis/script/*.lua`에 위치
- 복잡한 script는 단위 테스트와 Redis integration test를 모두 작성

### Auto Configuration

`repository-redis` 모듈은 필요한 Redis 구현체를 auto-configuration으로 등록한다.

```kotlin
@AutoConfiguration
class {Domain}RepositoryAutoConfiguration {

    @Bean
    fun {domain}RedisRepository(
        redisTemplate: StringRedisTemplate,
        objectMapper: ObjectMapper,
    ): {Domain}RedisRepository {
        return {Domain}RedisRepository(
            redisTemplate = redisTemplate,
            objectMapper = objectMapper,
        )
    }

    @Bean
    fun {domain}Repository(
        {domain}RedisRepository: {Domain}RedisRepository,
    ): {Domain}Repository {
        return {Domain}RepositoryImpl(
            {domain}RedisRepository = {domain}RedisRepository,
        )
    }
}
```

## 작명 방식

- 포트 구현체: `{Domain}RepositoryImpl`
- Redis 저장소: `{Domain}RedisRepository`
- Redis key: `{Domain}Key`, `{UseCase}Key`, `EntityRedisKey`
- Redis value: `{Domain}RedisValue`, `EntityRedisValue`
- Lua script: `{UseCase}Script`, `{Operation}Script`
- auto-configuration: `{Domain}RepositoryAutoConfiguration`

## gradle.properties

- `type=kotlin-boot-repository-redis`
- `group={base-package}.{domain}`
- `label=docker`

## Test

- 기본: `:{domain}:repository-redis:integrationTest`
- repository test
- auto-configuration load test
- Redis key serialization test
- Redis value serialization/deserialization test
- TTL test
- Lua script test
- Redis 자료구조별 동작 test
	- String
	- Hash
	- Set
	- ZSet
	- Stream


### Test 기준

Redis integration test는 Testcontainers Redis를 사용한다.

검증 대상은 다음과 같다.

- key namespace가 의도대로 생성되는지
- TTL이 정상적으로 설정되는지
- value serialization/deserialization이 깨지지 않는지
- Lua script가 원자성을 보장하는지
- Redis 자료구조별 정렬, 조회, 삭제가 기대한 대로 동작하는지
- 중복 실행 시 의도한 결과가 보장되는지
- auto-configuration만으로 repository bean이 로드되는지
