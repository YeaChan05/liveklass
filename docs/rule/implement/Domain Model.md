도메인 모델은 비즈니스 개념을 추상화한 대상이다

그래서 도메인 모델은 비즈니스 요구사항을 해소하기 위한 필드와 메서드를 추상체로 갖는다

실제 모델은 두가지 속성을 갖는다

### Identifier
Identifier는 도메인 모델을 식별하기 위한 식별자로 사용된다

실제 형태는 다음과 같다

```kotlin
interface {Domain}Identifier {  
    val {domain}Id: Long?  
}
```


### Properties
Properties는 도메인 모델의 실제 속성값이다

```kotlin
interface {Domain}Props {  
    // some fields
}
```


### Model
Model은 도메인 규칙, 행위들을 가진다

```kotlin
interface {Domain}Model :  
    {Domain}Props,  
    {Domain}Identifier {  
    fun someDomainMethod()
}
```


### 구현 예시

이 모델을 구현하는 대표적인 예시는 JPA Entity다

```kotlin
@Entity  
@Table(name = "{table_name}", catalog = "core")  
class {Domain}Entity() :  
    BaseEntity(),  
    {Domain}Model {  
    override val {domain}Id: Long?  
        get() = id  
  
    @field:Column(nullable = false)  
    override var someColumn: Long? = null
    ...
```


JPA Entity는 `BaseEntity`의 `id`를 기본 식별자로 발급받는데 이를 주입받아 `Identifier`의 추상 필드를 주입한다

```kotlin
@MappedSuperclass  
abstract class BaseEntity protected constructor() {  
    @field:Id  
    @field:Tsid    
    var id: Long? = null  
        protected set
	...
```

