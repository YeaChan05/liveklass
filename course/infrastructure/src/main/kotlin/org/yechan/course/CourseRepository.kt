package org.yechan.course

interface CourseRepository {
    fun findById(courseId: Long): CourseModel?

    fun findAll(): List<CourseModel>

    fun findAllByStatus(status: CourseStatus): List<CourseModel>

    fun save(course: CourseModel): CourseModel

    fun reserveSeatIfAvailable(courseId: Long): Boolean

    fun releaseSeatIfPossible(courseId: Long): Boolean

    fun findAllOpendCoursesByIds(courseIds: Collection<Long>): List<CourseModel>
}
