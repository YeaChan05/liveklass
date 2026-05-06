### Security Configuration

프로젝트의 전역 보안 정책은 `common-security` 모듈에서 관리되며, `CommonSecurityAutoConfiguration`을 통해 자동 설정된다

#### 1. 인증 필터 체인

두 종류의 인증 필터가 순차적으로 적용된다

- **InternalServiceAuthenticationFilter**: 내부 서비스 간 호출 시 사용되는 헤더(`X-Internal-Service-Auth`)를 검증한다 최우선으로 실행된다
- **JwtAuthenticationFilter**: `Authorization` 헤더의 Bearer 토큰을 검증하여 사용자를 식별한다

#### 2. 무상태성 (Stateless)

REST API 서버로서 세션을 유지하지 않으며, 모든 요청에 대해 토큰 기반 인증을 수행한다
`SessionCreationPolicy.STATELESS`로 설정한다

#### 3. 유연한 권한 설정 (AuthorizeHttpRequestsCustomizer)

특정 모듈이나 엔드포인트에서 권한 설정을 확장해야 할 경우, `AuthorizeHttpRequestsCustomizer` 빈을 등록하여 필터 체인에 동적으로 추가할 수 있다

- 기본 정책: `anyRequest().authenticated()` (모든 요청 인증 필요)
- 확장 예시: Swagger UI 경로 허용, 로그인 API 허용 등

#### 4. 보안 예외 처리

인증 실패(`AuthenticationEntryPoint`)나 권한 부족(`AccessDeniedHandler`) 상황에 대해 `common-api`의 응답 규격과 일치하는 JSON 응답을 내려주도록 커스텀 구현되어 있다
