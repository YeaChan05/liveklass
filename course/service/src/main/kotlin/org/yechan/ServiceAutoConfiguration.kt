package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import org.yechan.course.CourseCommandProcessor
import org.yechan.course.CourseQueryProcessor
import org.yechan.course.CourseService
import org.yechan.course.CourseUseCase
import org.yechan.enrollment.EnrollmentExpirationProcessor
import org.yechan.enrollment.EnrollmentExpirationService
import org.yechan.enrollment.EnrollmentPaymentExpirationScheduler
import org.yechan.enrollment.EnrollmentPaymentExpirationService
import org.yechan.enrollment.EnrollmentPaymentPendingProperties
import org.yechan.enrollment.EnrollmentService
import org.yechan.enrollment.EnrollmentTransactionService
import org.yechan.enrollment.EnrollmentUseCase
import org.yechan.enrollment.EnrollmentWaitlistCoordinator
import org.yechan.enrollment.EnrollmentWaitlistProcessor
import org.yechan.enrollment.EnrollmentWaitlistPromotionService
import org.yechan.enrollment.EnrollmentWaitlistScheduler
import org.yechan.enrollment.WaitlistPromotionRecoveryService
import org.yechan.member.MemberAuthService
import org.yechan.member.MemberAuthUseCase
import org.yechan.member.MemberCurrentMemberProcessor
import org.yechan.member.MemberRegistrationProcessor
import org.yechan.member.MemberSessionProcessor
import java.time.Clock

@Import(ServiceBeanRegistrar::class)
@EnableConfigurationProperties(EnrollmentPaymentPendingProperties::class)
@AutoConfiguration
class ServiceAutoConfiguration

@AutoConfiguration
class ServiceBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<CourseQueryProcessor> {
            CourseQueryProcessor(bean())
        }
        registerBean<CourseCommandProcessor> {
            CourseCommandProcessor(
                bean(),
                bean(),
            )
        }
        registerBean<CourseUseCase> {
            CourseService(
                bean<CourseQueryProcessor>(),
                bean<CourseCommandProcessor>(),
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
        registerBean<EnrollmentWaitlistCoordinator> {
            EnrollmentWaitlistCoordinator(
                bean(),
                bean(),
            )
        }
        registerBean<WaitlistPromotionRecoveryService> {
            WaitlistPromotionRecoveryService(
                bean(),
                bean(),
                bean(),
            )
        }
        registerBean<EnrollmentWaitlistScheduler> {
            EnrollmentWaitlistScheduler(
                bean<WaitlistPromotionRecoveryService>(),
            )
        }

        registerBean<MemberRegistrationProcessor> {
            MemberRegistrationProcessor(
                bean(),
                bean(),
            )
        }
        registerBean<MemberSessionProcessor> {
            MemberSessionProcessor(
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
        registerBean<MemberCurrentMemberProcessor> {
            MemberCurrentMemberProcessor(bean())
        }
        registerBean<MemberAuthUseCase> {
            MemberAuthService(
                bean<MemberRegistrationProcessor>(),
                bean<MemberSessionProcessor>(),
                bean<MemberCurrentMemberProcessor>(),
            )
        }

        registerBean<EnrollmentPaymentExpirationService> {
            EnrollmentPaymentExpirationService(
                bean(),
                bean(),
                bean(),
                bean(),
            )
        }
        registerBean<EnrollmentPaymentExpirationScheduler> {
            EnrollmentPaymentExpirationScheduler(
                bean<EnrollmentPaymentExpirationService>(),
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
