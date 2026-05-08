package org.yechan.course

interface CourseRepository {
    fun save(course: CourseModel): CourseModel

    fun findById(courseId: Long): CourseModel?

    fun findAll(): List<CourseModel>
}
