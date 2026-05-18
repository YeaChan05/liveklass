package org.yechan.enrollment

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Import

@AutoConfiguration
@ConditionalOnBean(EnrollmentUseCase::class)
@Import(EnrollmentApiBeanRegistrar::class)
class EnrollmentApiConfiguration

class EnrollmentApiBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<EnrollmentWaitlistSseHandler> {
            EnrollmentWaitlistSseHandler(
                bean(),
            )
        }
    })
