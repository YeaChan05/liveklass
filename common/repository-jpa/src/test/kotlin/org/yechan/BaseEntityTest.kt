package org.yechan

import org.assertj.core.api.Assertions
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class BaseEntityTest {
    @Test
    fun `같은 아이디를 가진 엔티티는 서로 같다`() {
        val first = TestEntity()
        val second = TestEntity()
        setId(first, 1L)
        setId(second, 1L)

        Assertions.assertThat(first).isEqualTo(second)
    }

    @Test
    fun `다른 아이디를 가진 엔티티는 서로 다르다`() {
        val first = TestEntity()
        val second = TestEntity()
        setId(first, 1L)
        setId(second, 2L)

        Assertions.assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `아이디가 없는 엔티티는 서로 다르다`() {
        val first = TestEntity()
        val second = TestEntity()
        setId(second, 1L)

        Assertions.assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `같은 유효 클래스를 가진 엔티티는 같은 해시 코드를 공유한다`() {
        val first = TestEntity()
        val second = TestEntity()
        setId(first, 1L)
        setId(second, 1L)

        Assertions.assertThat(first.hashCode()).isEqualTo(second.hashCode())
    }

    @Test
    fun `hibernate proxy 비교는 persistent class를 사용한다`() {
        val entity = TestEntity()
        setId(entity, 1L)
        val initializer = Mockito.mock(LazyInitializer::class.java)
        Mockito.`when`(initializer.persistentClass).thenAnswer { TestEntity::class.java }
        val proxy = TestEntityProxy(initializer)
        setId(proxy, 1L)

        Assertions.assertThat(entity).isEqualTo(proxy)
        Assertions.assertThat(proxy).isEqualTo(entity)
    }

    private fun setId(
        entity: BaseEntity,
        id: Long,
    ) {
        val field = BaseEntity::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, id)
    }

    private open class TestEntity : BaseEntity()

    private class TestEntityProxy(
        private val initializer: LazyInitializer,
    ) : TestEntity(),
        HibernateProxy {
        override fun getHibernateLazyInitializer(): LazyInitializer = initializer

        override fun writeReplace(): Any = this
    }
}
