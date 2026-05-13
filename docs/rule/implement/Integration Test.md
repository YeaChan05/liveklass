### 통합 테스트 (Integration Test)

각 컴포넌트 간의 상호작용과 전체 비즈니스 흐름을 검증하기 위해 통합 테스트를 수행한다

#### 1. 테스트 원칙

- **실제 환경과 유사하게**: 가능한 한 실제 인프라(MySQL, Redis)와 연동하여 테스트를 수행한다 이를 위해 `Testcontainers`를 활용한다
- **격리된 테스트**: 각 테스트 메서드 실행 전후로 데이터베이스 상태를 초기화하여 테스트 간 간섭을 방지한다
- **명확한 시나리오**: 단순 API 호출 검증을 넘어, 동시성 테스트나 복잡한 비즈니스 시나리오를 검증한다

#### 2. @SpringBootTest 활용

전체 애플리케이션 컨텍스트를 로드하여 테스트를 수행한다 runnable application 모듈의 `Application` 클래스를 설정 클래스로 지정하여 모든 핵심 로직을 통합 검증한다

```kotlin
@SpringBootTest(
    classes = [Application::class],
    // 필요 시 설정 주입
)
class PostSpecs {
    @Autowired
    lateinit var someUseCase: SomeUseCase
    // ... 기타 의존성 주입
}
```

#### 3. 동시성 및 비동기 검증

동시성 제어가 필요한 경우, `ExecutorService`와 `CountDownLatch`를 사용하여 실제 경합 상황을 재현하고 검증한다

#### 4. 데이터 초기화

테스트 간 독립성을 보장하기 위해 `EntityManager`를 사용하여 모든 테이블의 데이터를 삭제하는 `reset()` 메서드를 `@BeforeEach`에서 호출한다

#### 5. 테스트용 Props 정의

실제 요청 DTO 대신 테스트 코드 내부에 `TestMemberProps`등 인터페이스를 구현한 데이터 클래스를 정의하여 도메인 객체 생성에 필요한 필드들을 유연하게 구성한다

#### 6. 테스트 작명

의도를 명확히 파악할 수 있도록 이름을 작성한다

```kotlin
@Test  
fun `property 값이 다르면 bean을 등록하지 않는다`() {
...
}
```

테스트 클래스명은 요청 엔드포인트 패키지를 기준으로 다음과 같이 작명한다

예를 들어 `POST /api/enrollments`는 다음과 같은 테스트 클래스에서 통합 테스트가 이루어진다
```kotlin
// application 모듈 내 integrationTest
package com.example.enrollment
class PostSpecs {
	...
}
```
## 코드 작성 방식
- JUnit 5와 AssertJ를 사용한다
- 단위 테스트 클래스는 `*Test`로 끝나야 한다
- AAA(Arrange/Act/Assert) 패턴을 따르며 주석으로 구분한다
