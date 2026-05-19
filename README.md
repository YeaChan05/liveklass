## 프로젝트 개요

수강 신청과 결제 대기 상태를 다루는 Kotlin/Spring Boot 기반 백엔드 프로젝트입니다.

핵심 흐름은 강의 생성, 회원 인증, 수강 신청, 결제 대기 확정, 만료 후 대기열 승격입니다.
동시 수강 신청 상황에서는 MySQL을 기준 데이터 저장소로 사용하고 Redis는 대기열과 토큰 보조 저장소로 사용합니다.

프로젝트는 `course` 도메인 모듈과 공통 모듈인 `common`으로 나뉩니다.

```text
course
  application       Spring Boot 실행 모듈
  api               외부 API 컨트롤러와 요청/응답 모델
  service           유스케이스와 트랜잭션 오케스트레이션
  model             도메인 모델
  exception         도메인 예외
  repository-jpa    MySQL/JPA 저장소 어댑터
  repository-jdbc   벌크 쓰기용 JDBC 저장소 어댑터
  repository-redis  Redis 대기열/토큰 저장소 어댑터
  infrastructure    도메인 외부 인프라 어댑터
  schema            Liquibase 스키마 정의

common
  api               공통 API 응답/예외 처리
  boot              BeanRegistrar DSL 등 부트스트랩 유틸
  exception         공통 상태/예외 모델
  model             공통 값 모델
  repository-jpa    공통 JPA 기반 클래스
  security          공통 보안 설정
```

## 기술 스택

- **Language**: Kotlin 2.2.21, Java 21
- **Framework**: Spring Boot 4.0.6
- **Database**: MySQL 8.4.8, Redis 7.4
- **Migration**: Liquibase
- **Test**: JUnit 5, Spring Boot Test, Testcontainers 2.0.3, k6
- **Build Tool**: Gradle Kotlin DSL, ktlint, Jacoco
- **Observability**: Prometheus, Loki, Promtail, Grafana

## 실행 방법

### 애플리케이션 실행

애플리케이션 실행 시 `spring-boot-docker-compose`가 `docker-compose.yaml`의 MySQL과 Redis를 함께 구동합니다.

```bash
./gradlew :course:application:bootRun
```

> Docker가 실행 중이어야 합니다. Prometheus, Loki, Promtail, Grafana도 같은 Compose 파일에 정의되어 있으며 Grafana는 기본적으로 `http://localhost:3000`에서 확인할 수 있습니다.

## API 목록 및 예시

[API 명세서](docs/api/API%20명세.md)를 참고하세요.

## 데이터 모델 설명

[데이터 모델 명세서](docs/data/데이터%20모델%20명세.md)를 참고하세요.

## 요구사항 해석 및 가정

[요구사항 해석 및 가정](docs/requirement/요구사항%20해석%20및%20가정.md)를 참고하세요.

## 설계 결정과 이유

[설계 결정과 이유](docs/domain/설계%20결정과%20이유.md)를 참고하세요.

## 테스트 실행 방법

[테스트 실행 가이드](docs/test/테스트%20실행%20가이드.md)를 참고하세요.

## E2E 정합성 테스트 실행

k6 기반 정합성 시나리오 실행 방법은 [테스트 실행 가이드](docs/test/테스트%20실행%20가이드.md)의 E2E 정합성 테스트 섹션을 참고하세요.

## 미구현 / 제약사항

- 요구사항에 따라 외부 결제 시스템은 연동하지 않았습니다. 결제 확정은 실제 결제 승인 대신 수강 신청 상태를 `CONFIRMED`로 변경하는 방식으로 처리합니다.
- 결제 실패, 환불, 결제 재시도는 구현하지 않았습니다.
- `CONFIRMED` 상태의 수강 신청은 취소할 수 없습니다.
- `EXPIRED` 또는 `CANCELLED` 상태 이후 같은 강의에 다시 신청할 수 있습니다. 대신 동일한 course/member/status 조합의 중복 신청은 허용하지 않습니다.
- 대기열 등록은 지원하지만 대기열 사용자에게 별도 알림을 보내는 기능은 구현하지 않았습니다.
- `GET /api/enrollments/waitlist/me`는 현재 로그인한 회원의 대기열 상태를 SSE로 조회합니다.
- `DELETE /api/enrollments/waitlist/{courseId}`로 현재 로그인한 회원의 해당 강의 대기열을 취소할 수 있습니다.
- 강의 수강 기간에 따라 자동으로 모집을 시작하거나 마감하지 않습니다. 모집 시작과 마감은 `CREATOR`가 직접 수행합니다.
- 관리자에 의한 강의 수정, 강제 취소, 회원 관리 기능은 과제 범위에 명시되지 않아 포함하지 않았습니다.

## AI 활용 범위

- Codex CLI와 OMX 기반 에이전트 런타임을 활용해 개발했습니다.
- [AGENTS.md](AGENTS.md)를 통해 [WORKFLOW.md](docs/WORKFLOW.md)와 [RULE.md](docs/RULE.md)를 참조하는 작업 규칙을 적용했습니다.
- 에이전트 작업은 요구사항 분석, 테스트 작성, RED 확인, 최소 구현, GREEN 확인, 리팩터링, 필요한 문서 갱신 순서로 진행합니다.
- 모듈화를 통한 의존 강제, Jacoco, ktlint, graphify 지식 그래프를 함께 사용해 코드 품질을 관리합니다.
