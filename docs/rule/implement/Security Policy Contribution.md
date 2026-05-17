### Security Policy Contribution

보안 설정은 보통 하나의 거대한 `SecurityFilterChain` 설정 파일에 모든 공개 경로와 예외 규칙을 몰아넣기 쉽다

프로젝트가 커질수록 이 방식은 다음 문제를 만든다

- 새 모듈이 생길 때마다 중앙 보안 설정을 계속 수정해야 한다
- 어떤 경로를 왜 공개했는지 모듈 가까이에서 읽기 어렵다
- 기본 정책보다 예외 규칙이 먼저 적용되어야 하는 순서를 놓치기 쉽다

현재 프로젝트는 이를 해결하기 위해 **공통 보안 체인 + 모듈별 정책 기여(contribution)** 구조를 사용한다

#### 1. 기본 구조

공통 보안 모듈은 전체 필터 체인과 기본 fallback 정책만 가진다

- 인증 필터 등록
- 예외 처리기 등록
- 세션/CSRF 같은 전역 보안 설정
- 마지막 fallback 규칙: `anyRequest().authenticated()`

반면 각 application/module은 자신이 열어야 하는 공개 경로만 `AuthorizeHttpRequestsCustomizer`로 기여한다

즉, “보안 체인의 소유권”과 “공개 경로 정책의 소유권”을 분리한다

#### 2. 정책 표현 방식

모듈은 먼저 공개 엔드포인트 정책을 데이터로 표현한다

```kotlin
data class OpenEndpointMatcher(
    val method: HttpMethod? = null,
    val pattern: String,
)

data class StaticApplicationOpenEndpointPolicy(
    val includeHealth: Boolean = false,
    val additionalMatchers: List<OpenEndpointMatcher> = emptyList(),
)
```

이후 이 정책을 실제 Spring Security registry에 반영하는 customizer를 등록한다

```kotlin
class {Domain}ApplicationSecurityBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<ApplicationOpenEndpointPolicy> {
            StaticApplicationOpenEndpointPolicy(
                includeHealth = true,
                additionalMatchers = listOf(
                    OpenEndpointMatcher(HttpMethod.POST, "/auth/login"),
                ),
            )
        }

        registerBean<AuthorizeHttpRequestsCustomizer>("{domain}ApplicationAuthorizeHttpRequestsCustomizer") {
            PrioritizedAuthorizeHttpRequestsCustomizer(
                Ordered.HIGHEST_PRECEDENCE,
                ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer(
                    bean<ApplicationOpenEndpointPolicy>(),
                ),
            )
        }
    })
```

#### 3. 순서 제어

정책 기여 방식에서 가장 중요한 것은 **순서(order)** 다

- 모듈 customizer는 `Ordered.HIGHEST_PRECEDENCE` 같은 높은 우선순위로 등록한다
- 공통 fallback customizer는 `Ordered.LOWEST_PRECEDENCE`로 등록한다
- 최종 `SecurityFilterChain`은 customizer를 정렬된 순서대로 적용한다

이렇게 하면 각 모듈의 permit rule이 먼저 적용되고, 마지막에 공통 “닫는 규칙”이 남은 경로를 보호한다

#### 4. 장점

- 모듈이 자신의 공개 경로를 스스로 선언할 수 있다
- 중앙 보안 설정이 모든 엔드포인트 세부사항을 알 필요가 없다
- fallback 규칙이 명확해져 “실수로 열린 경로”를 줄일 수 있다
- 정책이 data object로 분리되어 테스트와 재사용이 쉬워진다
- Swagger, health, auth endpoint 같은 공통 예외를 일관되게 다루기 좋다

#### 5. 테스트 방식

이 패턴은 반드시 순서를 테스트해야 한다

권장 테스트는 아래 두 가지다

1. customizer bean이 기대한 순서로 정렬되는지 검증한다
2. 열려야 하는 경로는 실제로 열리고, 나머지는 fallback에 의해 막히는지 검증한다

즉, 단순 bean 존재 여부보다 “정책 적용 결과”까지 같이 확인하는 것이 중요하다

#### 6. 주의할 점

- 공개 경로 목록이 data object와 customizer 구현 사이에서 중복되면 안 된다
- 모듈이 너무 많은 세부 경로를 직접 만지기 시작하면 정책이 다시 흩어진다
- permit rule은 최소 범위로 유지하고, 나머지는 반드시 fallback에 맡겨야 한다
- order 없는 customizer를 여러 개 섞으면 해석 순서가 불분명해질 수 있다

#### 7. 언제 유용한가

- 멀티 모듈 Spring Security 프로젝트
- 공통 인증 체인은 유지하면서 공개 경로만 모듈별로 다르게 가져가야 하는 프로젝트
- Swagger/health/auth endpoint 예외가 자주 바뀌는 프로젝트
- 보안 정책을 중앙 집중식 하드코딩보다 모듈 기여 방식으로 관리하고 싶은 프로젝트

#### 8. Swagger/OpenAPI 공개 정책

Swagger/OpenAPI endpoint는 API 문서 탐색을 위한 공개 endpoint로 다룬다.
다만 공통 `SecurityFilterChain`을 직접 수정하지 않고, 별도 `AuthorizeHttpRequestsCustomizer` 기여로만 허용한다.

허용 범위는 다음처럼 최소 경로로 제한한다.

- `GET /v3/api-docs`
- `GET /v3/api-docs/**`
- `GET /v3/api-docs.yaml`
- `GET /swagger-ui.html`
- `GET /swagger-ui/**`

이 정책은 공통 fallback인 `anyRequest().authenticated()`보다 먼저 적용되어야 한다.
Swagger 경로 외의 일반 endpoint가 함께 열리지 않는지 테스트로 확인한다.
