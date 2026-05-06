### @LoginUserId

현재 로그인한 사용자의 식별자(memberId)를 컨트롤러 메서드 파라미터로 직접 주입받기 위해 사용되는 어노테이션이다

#### 1. 사용 예시

```kotlin
@GetMapping("/me")
fun getMyAccount(
    @LoginUserId memberId: Long,
) {
    // memberId를 사용하여 비즈니스 로직 수행
}
```

#### 2. 동작 원리

`LoginUserIdArgumentResolver`가 `HandlerMethodArgumentResolver`로서 동작하며 다음 과정을 거친다

1. `supportsParameter`: 파라미터에 `@LoginUserId`가 붙어 있고 타입이 `Long`인지 확인한다
2. `resolveArgument`: `SecurityContextHolder`에서 `Authentication` 객체를 가져온다
	1. 인증된 사용자의 `name`(문자열 형태의 식별자)을 `Long`으로 변환하여 반환한다
	2. 만약 인증되지 않은 경우(`AnonymousAuthenticationToken` 등) `BusinessException`을 발생시켜 401 응답을 유도한다

```kotlin
class LoginUserIdArgumentResolver : HandlerMethodArgumentResolver {  
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.hasParameterAnnotation(LoginUserId::class.java) &&  
        (parameter.parameterType == Long::class.javaObjectType || parameter.parameterType == Long::class.javaPrimitiveType)  
  
    override fun resolveArgument(  
        parameter: MethodParameter,  
        mavContainer: ModelAndViewContainer?,  
        webRequest: NativeWebRequest,  
        binderFactory: WebDataBinderFactory?,  
    ): Any {  
        val authentication = SecurityContextHolder.getContext().authentication  
        if (authentication == null || authentication is AnonymousAuthenticationToken) {  
            throw BusinessException(Status.AUTHENTICATION_FAILED, "Unauthorized")  
        }  
  
        return authentication.name.toLongOrNull()  
            ?: throw BusinessException(Status.BAD_REQUEST, "Invalid user id")  
    }  
}
```

#### 3. 장점

- 컨트롤러 코드에서 `SecurityContextHolder`를 직접 참조할 필요가 없다
- 비즈니스 로직에서 필요한 사용자 식별자를 명시적으로 드러낼 수 있다
- 타입 안전성을 보장한다 (Long)
