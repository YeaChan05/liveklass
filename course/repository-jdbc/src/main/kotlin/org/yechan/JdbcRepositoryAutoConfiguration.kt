package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import
import org.yechan.course.CourseBulkWriterImpl
import org.yechan.enrollment.CourseBulkWriter
import org.yechan.enrollment.EnrollmentBulkWriter
import org.yechan.enrollment.EnrollmentBulkWriterImpl

@Import(JdbcRepositoryBeanRegistrar::class)
@AutoConfiguration
class JdbcRepositoryAutoConfiguration
class JdbcRepositoryBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<EnrollmentBulkWriter> {
            EnrollmentBulkWriterImpl(bean())
        }

        registerBean<CourseBulkWriter> {
            CourseBulkWriterImpl(bean())
        }
    })
