## 프로젝트 개요
## 기술 스택
- **Language**: Kotlin 2.2.21
- **Framework**: Spring Boot 4.0.6
- **Database**: MySQL 8.4.8 (Docker), Redis 7.4 (Docker)
- **Infrastructure**: Docker Compose, Testcontainers 2.0.3(MySQL)
- **Build Tool**: Gradle (Kotlin DSL)
## 실행 방법

### 애플리케이션 실행

본 프로젝트는 `spring-boot-docker-compose`를 지원하므로, 애플리케이션 실행 시 필요한 인프라(MySQL, Redis)가 자동으로 구동됩니다.



```bash
./gradlew :course:application:bootRun
```

  
> Docker가 실행 중이어야 합니다. 별도로 인프라만 수동으로 구동하거나 외부 도구(IDE, DB Client 등)에서 접속이 필요한 경우에만 아래 명령어를 사용하세요.  


## API 목록 및 예시
[API 명세서](docs/api/API%20명세.md)를 참고하세요.  
  
## 데이터 모델 설명
[데이터 모델 명세서](docs/data/데이터%20모델%20명세.md)를 참고하세요.  
  
## 요구사항 해석 및 가정
[요구사항 해석 및 가정](docs/requirement/요구사항%20해석%20및%20가정.md)를 참고하세요.  
  
## 설계 결정과 이유
[설계 결정과 이유](docs/domain/도메인%20명세.md)를 참고하세요.

## 테스트 실행 방법
전체 테스트 실행
```bash
./gradlew test
```

모듈별 통합 테스트 실행 (Redis, MySQL 등 컨테이너 필요)
```bash
./gradlew :course:repository-redis:integrationTest
./gradlew :course:repository-jpa:integrationTest
```
- 테스트 시 Testcontainers를 사용하므로 로컬에 Docker가 실행 중이어야 합니다.

E2E 정합성 테스트 실행
```bash
MEMBER_TOKEN_GENERATOR_ENABLED=true \  
MEMBER_TOKEN_GENERATOR_COUNT=5000 \  
MEMBER_TOKEN_GENERATOR_OUTPUT_PATH=./k6/tokens.json \  
./gradlew :application-root:bootRun
```
애플리케이션 실행 후 k6가 실행 가능한 위치에서 아래 명령어로 정합성 검증 테스트를 실행할 수 있습니다.
```bash
# 단순 수강 신청 시나리오 실행
BASE_URL=http://localhost:8080 \
API_VERSION=v1 \
COURSE_CAPACITY=100 \
APPLICANT_COUNT=5000 \
VUS=1000 \
SETUP_TIMEOUT=1m \
TEARDOWN_TIMEOUT=1m \
SCENARIO_MAX_DURATION=3m \
GRACEFUL_STOP=30s \
TOKEN_PATH=../tokens.json \
k6 run k6/scenarios/concurrent-enroll-one-course.js
```

```bash
# 취소 + 수강 신청 시나리오 실행
BASE_URL=http://localhost:8080 \
API_VERSION=v1 \
COURSE_CAPACITY=100 \
APPLICANT_COUNT=5000 \
VUS=1000 \
SETUP_TIMEOUT=1m \
TEARDOWN_TIMEOUT=1m \
SCENARIO_MAX_DURATION=3m \
GRACEFUL_STOP=30s \
TOKEN_PATH=../tokens.json \
k6 run k6/scenarios/concurrent-enroll-after-cancel-one-course.js
```

- 정합성 검증 테스트를 진행하는 경우 MEMBER_TOKEN_GENERATOR_ENABLED=true 환경 변수를 설정하여 토큰 생성 기능을 활성화해야 합니다.
## 미구현 / 제약사항

- 요구사항에 따라 외부 결제 시스템은 연동하지 않았습니다. 결제 확정은 실제 결제 승인 대신 수강 신청 상태를 `CONFIRMED`로 변경하는 방식으로 처리했습니다.
	- 이에 따라 결제 실패, 환불, 결제 재시도는 구현하지 않았습니다.
- `CONFIRMED` 상태의 수강 신청은 취소할 수 없습니다.
- `CANCELLED` 또는 `EXPIRED` 상태 이후 같은 강의에 다시 신청하는 기능은 지원하지 않습니다.
- 대기열 등록은 지원하지만, 대기열 사용자에게 자동으로 입장 권한을 부여하거나 알림을 보내는 기능은 구현하지 않았습니다.
- 강의 수강 기간에 따라 자동으로 모집을 시작하거나 마감하지 않습니다. 모집 시작과 마감은 `CREATOR`가 직접 수행합니다.
- 관리자에 의한 강의 수정, 강제 취소, 회원 관리 기능은 과제 범위에 명시되지 않아 포함하지 않았습니다.

## AI 활용 범위
- codex cli를 활용한 에이전트 기반 개발을 진행했습니다.
- andrej-karpathy가 제시한 CLAUDE.md를 기반으로 작성한 [AGENTS.md](AGENTS.md)를 통해 [WORKFLOW.md](/docs/WORKFLOW.md)와 [RULE.md](/docs/RULE.md)를 사용한 하네스를 적용했습니다.
- 또한 OMX를 기반으로한 에이전트 런타임을 사용하여 개발을 진행했습니다.
- agent knowledge wiki를 위해 graphify를 사용했습니다.
- agent에게 전달하기 위한 문서는 /docs에 주로 작성합니다. 이 문서들은 개발자가 직접 작성합니다. 
- 에이전트 작업 흐름은 다음과 같습니다.
  - 요구사항 분석 -> 테스트 작성 -> RED 확인 -> 최소 구현 -> GREEN 확인 -> 리팩터링 -> 필요한 문서 갱신 순서로 작업합니다.
  - agent에게 모든것을 맡기지 않고 모듈화를 통한 의존 강제와 jacoco, ktlint를 사용해 코드 품질을 유지합니다.
- 에이전트 작업 규칙은 다음과 같습니다.
  - 코드 변경 전 /docs/rule의 문서들을 먼저 확인하고, 모듈 경계와 컨벤션을 준수합니다.
