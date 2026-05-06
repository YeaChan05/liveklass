### 네이밍 컨벤션

---

## 1. 공통 원칙

- 역할과 책임을 이름으로 유추할 수 있어야 한다
- 인터페이스는 계약, 구현체는 구현임이 명확해야 한다
- 같은 역할이면 애플리케이션 전반에 동일한 접미사를 사용한다

---

## 2. Core 영역 네이밍

### model (도메인)

- Aggregate Root

    - `DomainModel`
- 내부 구성 요소

    - `DomainIdentifier`
    - `DomainProps`
- 값 객체

    - `XXXValue`
    - `XXXAmount`
    - `XXXPolicy`

규칙

- Entity, DTO, VO 같은 기술적 용어 사용 x
- 불변 객체 우선

---

### service (use-case)

- 유스케이스 인터페이스

    - `DomainCreateUseCase`
    - `DomainReadUseCase`
    - `DomainUpdateUseCase`
    - `DomainQueryUseCase`

- 구현체

    - `DomainService`
    - `DomainQueryService`

규칙

- UseCase 단위로 책임 분리
- Service는 반드시 UseCase 인터페이스를 구현

---

### exception

- 모든 비즈니스 예외는 반드시 `:common:exception` 모듈의 `BusinessException`을 상속
- 네이밍

    - `DomainNotFoundException`
    - `DomainAlreadyExistsException`
    - `DomainInvalidStateException`
    - `DomainPermissionDeniedException`

규칙

- RuntimeException 직접 상속 x
- 기술 예외(SQLException 등) 직접 노출 x
- 의미 중심 네이밍 필수

---

### infrastructure (out-port)

- 포트 인터페이스

    - `DomainRepository`
    - `DomainEventPublisher`
    - `DomainExternalClient`

규칙

- 저장소/외부 연동의 역할만 표현
- 구현 기술 드러내는 이름 x

---

## 3. Inbound 어댑터 네이밍

### api (외부 공개)

- Controller

    - `{Domain}Controller` (기본)
    - `{Domain}ApiController` (필요 시)
- DTO

    - `{Domain}{Action}Request`
    - `{Domain}{Action}Response`
- web helper

    - `{Domain}{Action}Handler`
    - `{Domain}{Action}Registry`
    - `{Domain}{Action}Adapter`

규칙

- Controller는 use-case만 호출
- Domain 모델 직접 반환 x
- api 모듈의 web helper에 `UseCase` 접미사 사용 x

---

### api-operation (운영/관리)

- 현재 미사용(필요 시 추가)

---

### api-internal (서버 입장 내부 API)

- 패키지

    - `internal.contract`
    - `internal.adapter`
- 계약

    - `DomainInternalApi`
    - `{Domain}{Action}Request`
    - `{Domain}{Action}Response`
- 구현

    - `DomainInternalAdapter`

규칙

- consumer는 `internal.contract`만 import
- adapter는 provider `service`만 호출

---

### consumer-side internal client (infrastructure)

- Client

    - `DomainClient`
    - `DomainClientAdapter`
- transfer-local DTO

    - `DomainSnapshot`
    - `DomainCommand`
    - `DomainQuery`

규칙

- consumer `service`는 provider `api-internal` DTO를 직접 보지 않는다
- consumer `infrastructure`가 provider 계약을 transfer-local 타입으로 변환한다
- `api-internal-client` 는 현재 기본 구조가 아니며 future option으로만 남긴다

---

## 4. Outbound 어댑터 네이밍

### repository-{type}

- 구현체

    - `DomainRepositoryImpl`
- 기술 리포지토리

    - `DomainJpaRepository`
- 영속 엔티티
    - `DomainEntity`

규칙

- Entity ↔ Domain 변환은 Impl 책임
- Domain이 Entity를 알면 안 됨

---

## 5. application-{type}

- 클래스

    - `DomainApiApplication`
    - `DomainBatchApplication`

규칙

- 비즈니스 로직 포함 x
- Bean 조립, 설정, 실행만 담당

현재는 application-root만 runnable

---

## 6. 요약

- Domain은 기술을 모른다
- UseCase는 구현을 모른다
- Exception은 항상 BusinessException 계층
- Adapter만 기술을 안다
- 이름으로 역할·경계·의존성 방향을 유추할 수 있게 한다
