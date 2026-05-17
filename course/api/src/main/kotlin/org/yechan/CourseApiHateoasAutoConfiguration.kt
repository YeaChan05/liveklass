package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.yechan.course.CourseResponseHateoasLinkProvider
import org.yechan.enrollment.EnrollmentResponseHateoasLinkProvider
import org.yechan.member.MemberAuthResponseHateoasLinkProvider

@AutoConfiguration
class CourseApiHateoasAutoConfiguration :
    BeanRegistrarDsl({
        registerBean<ApiHateoasLinkProvider> {
            CourseResponseHateoasLinkProvider()
        }

        registerBean<ApiHateoasLinkProvider> {
            EnrollmentResponseHateoasLinkProvider()
        }

        registerBean<ApiHateoasLinkProvider> {
            MemberAuthResponseHateoasLinkProvider()
        }
    })
