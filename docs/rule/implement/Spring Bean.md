
현재 프로젝트는 일반적인 Spring Bean 등록 방식과는 살짝 다르다

두가지 관점에서 차이점이 있다

## 1. 모듈 의존을 통한 자동 설정 주입

현재 프로젝트는 멀티 모듈을 사용한 bean 등록 방식을 사용한다

그렇기에 피의존 모듈에서 등록한 bean이 의존 모듈에서 사용될 수 있어야한다

이를 위해 Spring Boot 자동 설정을 사용한다


```kotlin
@AutoConfiguration  
class {Domain}BeanRegistrar
```

등록할 bean 설정 파일을 위와 같이 `@AutoConfiguration`으로 등록하고 나면

```imports
#org.springframework.boot.autoconfigure.AutoConfiguration.imports
{base-package}.{domain}.{Domain}BeanRegistrar
```

위와 같이 classpath(resources/META-INF/spring)에 autocongifure에 주입해준다

다만 모듈내부에서만 사용하는 bean의 경우에는 `@Configuration`으로 충분하다

## 2. BeanRegistrarDsl을 사용한 Bean 등록

Spring Bean을 등록하는 방식도 일반적인 `@Bean` 사용을 하지 않는다

Spring 7의 `BeanRegistrar`의 Kotlin DSL인 `BeanRegistrarDsl`을 사용한다

```kotlin

@AutoConfiguration  
class {Domain}BeanRegistrar :  
    BeanRegistrarDsl({  
        registerBean<SomeBeanClass> {  
            AtherBean(
	            bean(),
	            bean()
            )  
        }  
    })
```

이렇게 했을때 장점은 AOT 활용도이다

`BeanRegistrarDsl`을 사용하면 bean 정의가 스캔 결과나 어노테이션 분산 선언에 덜 의존하므로, 애플리케이션의 bean 그래프를 더 명시적으로 유지할 수 있다

이는 Spring AOT가 빌드 시점에 컨텍스트를 분석하고 고정된 구성으로 최적화하는 방향과도 잘 맞는다


또한 피의존 클래스를 직접 명시적으로 드러낼 필요가 줄어들어 설정 코드에서 구체 타입을 직접 참조하는 빈도가 줄어든다

위 코드에서 `bean()`이라는 함수를 사용함으로써 Spring이 주입되어야할 bean을 추론하기 때문에 코드레벨의 `import` 필요성이 줄어든다(다만 경우에 따라서는 주입 class를 명시해야 할수도 있다)

```kotlin
import org.springframework.beans.factory.BeanRegistrarDsl  
import org.springframework.boot.autoconfigure.AutoConfiguration
```


### Reference
[[BeanRegistrarConditionalDsl]]