package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import org.yechan.course.CourseRepositoryReader
import org.yechan.course.CourseRepositoryWriter
import org.yechan.course.CourseService
import org.yechan.course.CourseUseCase
import org.yechan.enrollment.EnrollmentExpirationProcessor
import org.yechan.enrollment.EnrollmentExpirationService
import org.yechan.enrollment.EnrollmentPaymentExpirationScheduler
import org.yechan.enrollment.EnrollmentPaymentExpirationService
import org.yechan.enrollment.EnrollmentPaymentPendingProperties
import org.yechan.enrollment.EnrollmentRepositoryReader
import org.yechan.enrollment.EnrollmentRepositoryWriter
import org.yechan.enrollment.EnrollmentService
import org.yechan.enrollment.EnrollmentUseCase
import org.yechan.enrollment.EnrollmentWaitlistProcessor
import org.yechan.enrollment.EnrollmentWaitlistPromotionService
import org.yechan.enrollment.EnrollmentWaitlistRepositoryReader
import org.yechan.enrollment.EnrollmentWaitlistRepositoryWriter
import org.yechan.enrollment.EnrollmentWaitlistScheduler
import org.yechan.enrollment.WaitlistPromotionRecoveryService
import org.yechan.member.MemberAuthRepositoryWriter
import org.yechan.member.MemberAuthService
import org.yechan.member.MemberAuthUseCase
import org.yechan.member.MemberRepositoryReader
import java.time.Clock

@Import(ServiceBeanRegistrar::class)
@EnableConfigurationProperties(EnrollmentPaymentPendingProperties::class)
@AutoConfiguration
class ServiceAutoConfiguration

@AutoConfiguration
class ServiceBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<CourseRepositoryReader> {
            CourseRepositoryReader(bean())
        }
        registerBean<CourseRepositoryWriter> {
            CourseRepositoryWriter(
                bean(),
                bean(),
            )
        }
        registerBean<CourseUseCase> {
            CourseService(
                bean<CourseRepositoryReader>(),
                bean<CourseRepositoryWriter>(),
            )
        }

        registerBean<EnrollmentRepositoryReader> {
            EnrollmentRepositoryReader(
                bean(),
                bean(),
            )
        }
        registerBean<EnrollmentRepositoryWriter> {
            EnrollmentRepositoryWriter(
                bean(),
                bean(),
                bean<EnrollmentPaymentPendingProperties>().expiresIn,
            )
        }
        registerBean<EnrollmentUseCase> {
            EnrollmentService(
                bean<EnrollmentRepositoryReader>(),
                bean<EnrollmentRepositoryWriter>(),
                bean<EnrollmentWaitlistRepositoryReader>(),
                bean<EnrollmentWaitlistRepositoryWriter>(),
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
        registerBean<EnrollmentWaitlistRepositoryReader> {
            EnrollmentWaitlistRepositoryReader(bean())
        }
        registerBean<EnrollmentWaitlistRepositoryWriter> {
            EnrollmentWaitlistRepositoryWriter(
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

        registerBean<MemberAuthRepositoryWriter> {
            MemberAuthRepositoryWriter(
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
        registerBean<MemberRepositoryReader> {
            MemberRepositoryReader(bean())
        }
        registerBean<MemberAuthUseCase> {
            MemberAuthService(
                bean<MemberAuthRepositoryWriter>(),
                bean<MemberRepositoryReader>(),
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
