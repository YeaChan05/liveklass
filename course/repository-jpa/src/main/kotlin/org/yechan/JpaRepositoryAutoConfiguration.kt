package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import
import org.yechan.course.CourseRepository
import org.yechan.course.CourseRepositoryImpl
import org.yechan.enrollment.EnrollmentRepository
import org.yechan.enrollment.EnrollmentRepositoryImpl
import org.yechan.member.MemberRepository
import org.yechan.member.MemberRepositoryImpl

@Import(JpaRepositoryBeanRegistrar::class)
@AutoConfiguration
class JpaRepositoryAutoConfiguration
class JpaRepositoryBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<MemberRepository> {
            MemberRepositoryImpl(bean())
        }

        registerBean<CourseRepository> {
            CourseRepositoryImpl(bean())
        }

        registerBean<EnrollmentRepository> {
            EnrollmentRepositoryImpl(bean())
        }
    })
