## 개요

- 위치: `Driven`
- 역할: JPA persistence adapter, entity/domain 변환, auto-configuration
- 범위: 저장소 구현

## 의존 관계

### 의존 모듈

- `common:repository-jpa`
- `{domain}:infrastructure`
- `{domain}:schema`
- Liquibase

### 의존 금지 모듈

- `{domain}:service`
- `{domain}:api`
- `{domain}:api-internal`
- `{domain}:application`
- other-domain `repository-*`

## 구현

### 구성 요소

- `*RepositoryImpl`
- `*JpaRepository`
- `*Entity`
- `*RepositoryAutoConfiguration`

### 특징적 구현

```kotlin
@Entity
@Table(name = "{domain}", catalog = "core")
class {Domain}Entity() :
    BaseEntity(),
    {Domain}Model {
    override val {domain}Id: Long?
        get() = id
}

interface {Domain}JpaRepository :
    JpaRepository<{Domain}Entity, Long>

class {Domain}RepositoryImpl(
    private val {domain}JpaRepository: {Domain}JpaRepository,
) : {Domain}Repository
```

### 작명 방식

- 포트 구현체: `{Domain}RepositoryImpl`
- JPA 저장소: `{Domain}JpaRepository`
- 영속 엔티티: `{Domain}Entity`
- auto-configuration: `{Domain}RepositoryAutoConfiguration`

## gradle.properties

- `type=kotlin-boot-repository-jpa`
- `group={base-package}.{domain}`
- `label=docker`

## Test

- 기본: `:{domain}:repository-jpa:integrationTest`
- repository test
- auto-configuration load test
