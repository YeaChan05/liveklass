### BusinessException Handling

비즈니스 로직 수행 중 발생하는 예외는 시스템 예외와 구분하여 명시적으로 처리한다

#### 1. BusinessException 상속

모든 도메인 특화 예외는 반드시 `BusinessException`을 상속해야 한다 이를 통해 공통 예외 처리기(`GlobalExceptionHandler`)가 일관된 방식으로 응답을 내려줄 수 있다

```kotlin
// 예: 계좌 잔액 부족 예외
class InsufficientBalanceException(
    message: String = "잔액이 부족합니다"
) : BusinessException(Status.BAD_REQUEST, message)
```

#### 2. Status 사용

`BusinessException`은 `Status` 열거형을 포함한다 이 `Status`는 비즈니스적 의미를 담고 있으며, `toHttpStatus()` 메서드를 통해 적절한 HTTP 상태 코드로 변환된다

- `BAD_REQUEST` (400)
- `RESOURCE_NOT_FOUND` (404)
- `AUTHENTICATION_FAILED` (401)
- `INTERNAL_SERVER_ERROR` (500)

#### 3. GlobalExceptionHandler

`common-api` 모듈에 정의된 `GlobalExceptionHandler`는 `BusinessException` 및 그 하위 예외들을 포착하여 다음을 수행한다

1. 로그 기록: 예외 스택 트레이스를 에러 레벨로 기록한다
2. 응답 생성: `Status`에 대응하는 HTTP 상태 코드와 예외 메시지를 본문에 담아 반환한다

```kotlin
@ExceptionHandler(BusinessException::class)
fun handleBusinessException(e: BusinessException): ResponseEntity<Any> {
    log.error(e) { e.stackTraceToString() }
    return ResponseEntity.status(e.status.toHttpStatus()).body(e.message ?: "")
}
```
