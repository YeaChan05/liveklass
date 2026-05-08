package org.yechan.course

import org.springframework.data.jpa.repository.JpaRepository

interface CourseJpaRepository : JpaRepository<CourseEntity, Long>
