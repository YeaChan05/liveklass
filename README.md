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

> **참고**: Docker가 실행 중이어야 합니다. 별도로 인프라만 수동으로 구동하거나 외부 도구(IDE, DB Client 등)에서 접속이 필요한 경우에만 아래 명령어를 사용하세요.
## API 목록 및 예시
## 데이터 모델 설명
## 요구사항 해석 및 가정
## 설계 결정과 이유
## 테스트 실행 방법
전체 테스트 실행:
```bash
./gradlew test
```

모듈별 통합 테스트 실행 (Redis, MySQL 등 컨테이너 필요):
```bash
./gradlew :course:repository-redis:integrationTest
./gradlew :course:repository-jpa:integrationTest
```
- 테스트 시 Testcontainers를 사용하므로 로컬에 Docker가 실행 중이어야 합니다.
## 미구현 / 제약사항
- 현재 코스 수강 신청 및 관리 핵심 로직 위주로 구현되어 있습니다.
- 인프라 환경은 Docker를 기준으로 구성되어 있습니다.
## AI 활용 범위
