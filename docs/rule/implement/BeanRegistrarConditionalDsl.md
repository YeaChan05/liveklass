### BeanRegistrarConditionalDsl

`BeanRegistrarDsl` 환경에서 조건에 따라 Bean을 등록하거나 등록 여부를 결정할 때 사용하는 확장 기능이다

#### 1. registerIf

Spring `Environment` 정보를 기반으로 조건부 Bean 등록을 지원하며, `orElseIf`, `orElse`를 통한 체이닝이 가능하다

```kotlin
class SampleModeBeanRegistrar :
    BeanRegistrarDsl({
        registerIf(
            predicate = { env ->
                env.getProperty("sample.feature.mode") == "alpha"
            },
        ) {
            registerBean<String>("alpha") { "alpha" }
        }.orElseIf(
            predicate = { env ->
                env.getProperty("sample.feature.mode") == "beta"
            },
        ) {
            registerBean<String>("beta") { "beta" }
        }.orElse {
            registerBean<String>("fallback") { "fallback" }
        }
    })
```

- `predicate`: `Environment`를 인자로 받아 `Boolean`을 반환하는 조건 함수다
- `block`: 조건이 참일 때 실행될 Bean 등록 로직이다

#### 2. whenPropertyEnabled

특정 속성(Property)의 활성화 여부를 간편하게 확인하여 Bean을 등록한다 주로 기능 스위치(Feature Toggle)로 활용된다

```kotlin
class TransferOutboxBeanRegistrar :
    BeanRegistrarDsl({
        whenPropertyEnabled(
            prefix = "transfer.outbox",
            name = "enabled",
            matchIfMissing = true,
        ) {
            registerBean<TransferEventPublisher> {
                TransferEventPublisherImpl(
                    bean<RabbitTemplate>(),
                    bean<TransferEventPublisherProperties>(),
                )
            }
        }
    })
```

- `prefix`, `name`: 속성 키를 구성하는 요소다 (예: `transfer.outbox.enabled`)
- `havingValue`: 비교할 값으로, 기본값은 `"true"`다 (대소문자 구분 없음)
- `matchIfMissing`: 해당 속성이 없을 때 기본적으로 활성 상태로 간주할지 여부를 결정한다

#### 3. 사용 목적

- **환경별 차별화**: 로컬, 개발, 운영 환경에 따라 다른 구현체를 등록해야 할 때 유용하다
- **모듈 제어**: 특정 기능을 속성 설정만으로 쉽게 끄고 켤 수 있다
- **AOT 친화적**: 명시적인 코드로 조건을 제어하므로 Spring AOT 최적화 과정에서 Bean 구성을 더 정확하게 분석할 수 있다
