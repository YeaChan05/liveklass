VO를 JPA Entity로 사용해야 하는 상황이 있다

예를들면 다음과 같다

```kotlin
class Money private constructor(  
    private val value: BigDecimal,  
) : Comparable<Money> {  
    val amount: BigDecimal  
        get() = value  
  
    fun add(other: Money): Money = Money(normalize(value.add(other.value)))  
  
    fun subtract(other: Money): Money = Money(normalize(value.subtract(other.value)))  
  ...
}
```

공통 VO로 활용되는 경우에는 `common:model`에, 그 외에는 `{domain}:model`에 위치한다

그리고 이를 JPA해서 활용하기 위해서는 `common:repository-jpa` 또는 `{domain}:repository-jpa`에서 아래와 같이 `AttrebuteConverter`를 구현한다

```kotlin
@Converter(autoApply = true)  
class MoneyConverter : AttributeConverter<Money, BigDecimal> {  
  
    override fun convertToDatabaseColumn(attribute: Money?): BigDecimal? = attribute?.amount  
  
    override fun convertToEntityAttribute(dbData: BigDecimal?): Money? = dbData?.let { Money.of(it) }  
}
```

