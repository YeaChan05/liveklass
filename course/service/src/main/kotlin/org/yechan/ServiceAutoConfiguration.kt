package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import org.yechan.course.CourseService
import org.yechan.course.CourseUseCase
import org.yechan.enrollment.EnrollmentExpirationProcessor
import org.yechan.enrollment.EnrollmentExpirationService
import org.yechan.enrollment.EnrollmentPaymentExpirationScheduler
import org.yechan.enrollment.EnrollmentPaymentPendingProperties
import org.yechan.enrollment.EnrollmentService
import org.yechan.enrollment.EnrollmentTransactionService
import org.yechan.enrollment.EnrollmentUseCase
import org.yechan.enrollment.EnrollmentWaitlistProcessor
import org.yechan.enrollment.EnrollmentWaitlistPromotionService
import org.yechan.enrollment.EnrollmentWaitlistScheduler
import org.yechan.member.MemberAuthService
import org.yechan.member.MemberAuthUseCase
import java.time.Clock

@Import(ServiceBeanRegistrar::class)
@EnableConfigurationProperties(EnrollmentPaymentPendingProperties::class)
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

        registerBean<EnrollmentTransactionService> {
            EnrollmentTransactionService(
                bean(),
                bean(),
                bean<EnrollmentPaymentPendingProperties>().expiresIn,
            )
        }
        registerBean<EnrollmentUseCase> {
            EnrollmentService(
                bean(),
                bean(),
            )
        }
        registerBean<EnrollmentWaitlistProcessor> {
            EnrollmentWaitlistPromotionService(
                bean(),
                bean(),
                bean(),
                bean<EnrollmentPaymentPendingProperties>().expiresIn,
            )
        }
        registerBean<EnrollmentWaitlistScheduler> {
            EnrollmentWaitlistScheduler(
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

        registerBean<EnrollmentPaymentExpirationScheduler> {
            EnrollmentPaymentExpirationScheduler(
                bean(),
                bean(),
                bean(),
                bean(),
            )
        }

        registerBean<EnrollmentExpirationProcessor> {
            EnrollmentExpirationService(
                bean(),
                bean(),
            )
        }

        registerBean<Clock> {
            Clock.systemDefaultZone()
        }
    })
