package org.yechan.course

interface CourseRepository {
    fun save(course: CourseModel): CourseModel

    fun findById(courseId: Long): CourseModel?

    fun findAll(status: CourseStatus?): List<CourseModel>

    fun reserveSeatIfAvailable(courseId: Long): Boolean

    fun releaseSeatIfPossible(courseId: Long): Boolean
}
