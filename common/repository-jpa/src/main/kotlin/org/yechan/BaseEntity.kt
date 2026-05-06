package org.yechan

import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import org.hibernate.proxy.HibernateProxy
import java.time.LocalDateTime
import java.util.Objects

@MappedSuperclass
abstract class BaseEntity protected constructor() {
    @field:Column(updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @field:Column
    var updatedAt: LocalDateTime? = null
        protected set

    @field:Id
    @field:Tsid
    var id: Long? = null
        protected set

    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }

        val otherEffectiveClass =
            if (other is HibernateProxy) {
                other.hibernateLazyInitializer.persistentClass
            } else {
                other.javaClass
            }
        val thisEffectiveClass =
            if (this is HibernateProxy) {
                this.hibernateLazyInitializer.persistentClass
            } else {
                javaClass
            }
        if (thisEffectiveClass != otherEffectiveClass) {
            return false
        }

        other as BaseEntity
        return id != null && Objects.equals(id, other.id)
    }

    override fun hashCode(): Int = if (this is HibernateProxy) {
        this.hibernateLazyInitializer.persistentClass.hashCode()
    } else {
        javaClass.hashCode()
    }
}
