## 개요

- 위치: `Driven`
- 역할: 데이터베이스 스키마 정의, Liquibase changelog, 초기 데이터 시드
- 범위: 저장소 스키마 산출물 (리소스 전용)

## 의존 관계

### 의존 모듈

- 없음
- (런타임) Liquibase

### 의존 금지 모듈

- `{domain}:model`
- `{domain}:service`
- `{domain}:infrastructure`
- `{domain}:exception`
- `{domain}:api`
- `{domain}:api-internal`
- `{domain}:repository-*`
- `{domain}:mq-*`
- `{domain}:application`
- other-domain `schema`
- Spring
- JPA
- Web
- MQ

## 구현

### 구성 요소

- `db/changelog/{domain}/db.changelog-initial-schema.yaml`
- `db/changelog/{domain}/db.changelog-initial-schema-and-data.yaml`
- `db/changelog/{domain}/initial_schema.sql`
- `db/changelog/{domain}/data.sql`

### 특징적 구현

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