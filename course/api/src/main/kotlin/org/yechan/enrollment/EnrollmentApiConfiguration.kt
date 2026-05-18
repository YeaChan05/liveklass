package org.yechan.enrollment

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.context.annotation.Configuration

@Configuration
class EnrollmentApiConfiguration :
    BeanRegistrarDsl({
        registerBean<EnrollmentWaitlistSseHandler> {
            EnrollmentWaitlistSseHandler(
                bean(),
            )
        }
    })
