### Liquibase Rule

현재 프로젝트는 RDBMS에 해당하는 JPA Entity를 생성 및 수정할 때 이에 맞는 데이터베이스 스키마 마이그레이션 기록을 `{domain}:schema` 모듈에 기록한다

#### 1. 적용 대상

- JPA Entity를 새로 추가하면 Liquibase changelog를 함께 추가한다
- Entity의 테이블명, 컬럼명, 컬럼 타입, 길이, null 허용 여부, 기본키, 유니크 제약, 인덱스, 외래키가 변경되면 changelog를 함께 변경한다
- `BaseEntity`를 상속한 Entity는 공통 컬럼을 스키마에 포함한다
  - `id BIGINT NOT NULL`
  - `created_at DATETIME(6) NULL`
  - `updated_at DATETIME(6) NULL`
- Redis, MQ, 외부 API처럼 RDBMS 테이블을 만들지 않는 구현은 Liquibase 대상이 아니다

#### 2. 작성 위치

- Liquibase 파일은 `{domain}:schema` 모듈의 `src/main/resources` 아래에 둔다
- 도메인별 경로는 `db/changelog/{domain}`을 사용한다
- 초기 스키마는 다음 파일을 기본으로 사용한다
  - `db/changelog/{domain}/db.changelog-initial-schema.yaml`
  - `db/changelog/{domain}/initial_schema.sql`
- 초기 데이터가 필요하면 다음 파일을 추가한다
  - `db/changelog/{domain}/db.changelog-initial-schema-and-data.yaml`
  - `db/changelog/{domain}/data.sql`

#### 3. Changelog 작성 방식

- YAML changelog는 SQL 파일을 참조하는 얇은 진입점으로 작성한다
- 실제 DDL은 SQL 파일에 작성한다
- MySQL 기준으로 작성한다
- `sqlFile` 설정은 다음 형식을 따른다

```yaml
databaseChangeLog:
  - changeSet:
      id: {domain}-initial-schema
      author: course-{domain}
      changes:
        - sqlFile:
            dbms: mysql
            encoding: UTF-8
            endDelimiter: ';'
            path: db/changelog/{domain}/initial_schema.sql
            splitStatements: true
            stripComments: false
```

#### 4. DDL 작성 규칙

- 테이블명과 컬럼명은 Entity의 `@Table`, `@Column` 이름과 일치시킨다
- Kotlin/JPA 필드명이 camelCase이고 `@Column(name = "...")`이 없으면 snake_case 컬럼명으로 작성한다
  - 예: `passwordHash` → `password_hash`
  - 예: `createdAt` → `created_at`
- `Long` 식별자는 `BIGINT`로 작성한다
- 문자열 컬럼은 Entity의 `length`와 동일한 `VARCHAR(length)`로 작성한다
- `EnumType.STRING` 컬럼은 `VARCHAR(length)`로 작성한다
- `LocalDateTime` 컬럼은 `DATETIME(6)`으로 작성한다
- `nullable = false` 컬럼은 `NOT NULL`을 명시한다
- `unique = true` 컬럼은 명명된 unique constraint로 작성한다
- 기본키와 제약 조건은 이름을 명시한다
  - 기본키: `pk_{table_name}`
  - 유니크: `uk_{table_name}_{column_name}`
  - 인덱스: `idx_{table_name}_{column_name}`
  - 외래키: `fk_{from_table}_{to_table}`

#### 5. Entity와 Schema 동기화

- Entity 변경과 schema 변경은 같은 작업 단위에 포함한다
- Entity의 nullable, length, unique 속성과 SQL 정의가 다르면 안 된다
- `BaseEntity`의 `id`는 Entity마다 별도 primary key로 선언한다
- `created_at`, `updated_at`은 애플리케이션에서 채우므로 기본값을 임의로 넣지 않는다
- Entity에는 존재하지만 저장소 구현에서 사용하지 않는 RDBMS 테이블은 만들지 않는다

#### 6. 예시

```kotlin
@Entity
@Table(name = "members")
class MemberEntity(
    @field:Column(nullable = false, unique = true, length = 255)
    var email: String,
    @field:Column(name = "password_hash", nullable = false)
    var passwordHash: String,
) : BaseEntity()
```

```sql
CREATE TABLE members (
    id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_members PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email)
);
```

#### 7. 검증

- `{domain}:schema:jar`를 실행하여 changelog 리소스가 jar에 포함되는지 확인한다
- JPA Entity를 함께 수정한 경우 `{domain}:repository-jpa:test` 또는 `{domain}:repository-jpa:integrationTest`를 실행한다
- 애플리케이션 기동에 영향을 주는 변경이면 `{domain}:application:compileKotlin` 또는 애플리케이션 통합 테스트를 실행한다
- 문서와 예시 변경만 있는 경우에는 markdown 문법과 경로 일관성을 확인한다
