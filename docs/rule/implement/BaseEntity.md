
모든 JPA Entity는 공통 필드 및 동작을 정의하는 `BaseEntity`를 상속해야 한다

#### 1. 공통 필드

- `id`: `@Tsid`를 사용하여 고유 식별자를 발급받는다 64비트 정수형(Long)으로 시간 순서대로 정렬 가능하다
- `createdAt`: 데이터 생성 시각을 자동으로 기록한다 (`@PrePersist`)
- `updatedAt`: 데이터 수정 시각을 자동으로 기록한다 (`@PrePersist`, `@PreUpdate`)

#### 2. 구현 방식

```kotlin
@MappedSuperclass
abstract class BaseEntity protected constructor() {
    @field:Column(updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @field:Column
    var updatedAt: LocalDateTime? = null
        protected set

    @field:Id
    @field:Tsid
    var id: Long? = null
        protected set

    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
```

#### 3. equals & hashCode

JPA Entity의 동일성(Equality) 비교를 위해 `equals`와 `hashCode`를 재정의한다
이때, Hibernate의 지연 로딩(`HibernateProxy`)을 고려하여 `instanceof` 대신 `HibernateProxy`의 `persistentClass`를 사용하여 타입을 비교하고, 식별자(`id`)를 기준으로 비교를 수행한다

- `id`가 `null`인 경우(저장 전)에는 객체 주소 비교가 수행된다
- `id`가 존재하는 경우에는 클래스 타입과 `id`가 모두 일치해야 동일한 객체로 간주한다
