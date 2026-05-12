package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import
import org.yechan.course.CourseService
import org.yechan.course.CourseUseCase
import org.yechan.enrollment.EnrollmentExpirationService
import org.yechan.enrollment.EnrollmentExpirationUseCase
import org.yechan.enrollment.EnrollmentPaymentExpirationScheduler
import org.yechan.enrollment.EnrollmentService
import org.yechan.enrollment.EnrollmentUseCase
import org.yechan.enrollment.EnrollmentWaitlistScheduler
import org.yechan.member.MemberAuthService
import org.yechan.member.MemberAuthUseCase
import java.time.Clock

@Import(ServiceBeanRegistrar::class)
@AutoConfiguration
class ServiceAutoConfiguration

@AutoConfiguration
class ServiceBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<CourseUseCase> {
            CourseService(
                bean(),
                bean(),
            )
        }

        registerBean<EnrollmentUseCase> {
            EnrollmentService(
                bean(),
                bean(),
                bean(),
            )
        }
        registerBean<EnrollmentWaitlistScheduler> {
            EnrollmentWaitlistScheduler(
                bean(),
                bean(),
                bean(),
                bean(),
            )
        }

        registerBean<MemberAuthUseCase> {
            MemberAuthService(
                bean(),
                bean(),
                bean(),
                bean(),
                bean(),
                bean(),
                bean(),
                bean(),
            )
        }

        registerBean<EnrollmentExpirationUseCase> {
            EnrollmentExpirationService(
                bean(),
                bean(),
                bean(),
            )
        }

        registerBean<EnrollmentPaymentExpirationScheduler> {
            EnrollmentPaymentExpirationScheduler(
                bean(),
                bean(),
            )
        }

        registerBean<Clock> {
            Clock.systemUTC()
        }
    })
