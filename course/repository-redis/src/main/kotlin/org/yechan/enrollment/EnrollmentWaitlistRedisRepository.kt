package org.yechan.enrollment

import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant

class EnrollmentWaitlistRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) : EnrollmentWaitlistRepository {
    override fun enqueue(
        courseId: Long,
        memberId: Long,
        requestedAt: Instant,
    ) {
        zSetOperations.add(
            EnrollmentWaitlistRedisKey.byCourseId(courseId).value,
            EnrollmentWaitlistRedisValue.from(memberId, requestedAt).serialize(),
            requestedAt.toEpochMilli().toDouble(),
        )
        zSetOperations.add(
            EnrollmentWaitlistRedisKey.courseIds().value,
            courseId.toString(),
            courseId.toDouble(),
        )
    }

    override fun pop(courseId: Long): EnrollmentWaitlistEntry? {
        val key = EnrollmentWaitlistRedisKey.byCourseId(courseId)
        val member = zSetOperations.range(key.value, 0, 0)
            ?.firstOrNull()
            ?: return null

        zSetOperations.remove(key.value, member)
        cleanupCourseId(courseId)

        return EnrollmentWaitlistRedisValue.deserialize(member).toDomain(courseId)
    }

    override fun findByMemberId(memberId: Long): List<EnrollmentWaitlistEntry> = findCourseIds()
        .flatMap { courseId ->
            zSetOperations.range(EnrollmentWaitlistRedisKey.byCourseId(courseId).value, 0, -1)
                .orEmpty()
                .mapNotNull { serialized ->
                    EnrollmentWaitlistRedisValue.deserialize(serialized).toDomain(courseId)
                }
        }
        .filter { it.memberId == memberId }
        .sortedBy { it.requestedAt }

    override fun remove(
        courseId: Long,
        memberId: Long,
    ) {
        val key = EnrollmentWaitlistRedisKey.byCourseId(courseId)
        val serializedMemberIds = zSetOperations.range(key.value, 0, -1)
            .orEmpty()
            .filter { EnrollmentWaitlistRedisValue.deserialize(it).memberId == memberId }

        serializedMemberIds.forEach { zSetOperations.remove(key.value, it) }
        cleanupCourseId(courseId)
    }

    override fun findCourseIds(): Set<Long> = zSetOperations.range(EnrollmentWaitlistRedisKey.courseIds().value, 0, -1)
        .orEmpty()
        .mapNotNull(String::toLongOrNull)
        .toCollection(linkedSetOf())

    override fun isSoldOut(courseId: Long): Boolean = redisTemplate.hasKey(
        EnrollmentWaitlistRedisKey.soldOut(courseId).value,
    )

    override fun markSoldOut(courseId: Long) {
        redisTemplate.opsForValue().set(
            EnrollmentWaitlistRedisKey.soldOut(courseId).value,
            "true",
        )
    }

    override fun clearSoldOut(courseId: Long) {
        redisTemplate.delete(
            EnrollmentWaitlistRedisKey.soldOut(courseId).value,
        )
    }

    private val zSetOperations
        get() = redisTemplate.opsForZSet()

    private fun cleanupCourseId(courseId: Long) {
        val key = EnrollmentWaitlistRedisKey.byCourseId(courseId)
        if (zSetOperations.range(key.value, 0, 0).isNullOrEmpty()) {
            redisTemplate.delete(key.value)
            zSetOperations.remove(EnrollmentWaitlistRedisKey.courseIds().value, courseId.toString())
            clearSoldOut(courseId)
        }
    }
}

private sealed interface EnrollmentWaitlistRedisKey {
    val value: String

    data class ByCourseId(
        val courseId: Long,
    ) : EnrollmentWaitlistRedisKey {
        override val value: String = "$PREFIX:course:$courseId"
    }

    data class SoldOut(
        val courseId: Long,
    ) : EnrollmentWaitlistRedisKey {
        override val value: String = "$PREFIX:course:$courseId:sold-out"
    }

    data object CourseIds : EnrollmentWaitlistRedisKey {
        override val value: String = "$PREFIX:courses"
    }

    companion object {
        private const val PREFIX = "course:enrollment:waitlist"

        fun byCourseId(courseId: Long): EnrollmentWaitlistRedisKey = ByCourseId(courseId)

        fun soldOut(courseId: Long): EnrollmentWaitlistRedisKey = SoldOut(courseId)

        fun courseIds(): EnrollmentWaitlistRedisKey = CourseIds
    }
}

private data class EnrollmentWaitlistRedisValue(
    val memberId: Long,
    val requestedAt: Instant,
) {
    fun serialize(): String = listOf(
        memberId.toString().padStart(MEMBER_ID_WIDTH, '0'),
        requestedAt.toString(),
    ).joinToString(SEPARATOR)

    fun toDomain(courseId: Long): EnrollmentWaitlistEntry = EnrollmentWaitlistEntry(
        courseId = courseId,
        memberId = memberId,
        requestedAt = requestedAt,
    )

    companion object {
        private const val SEPARATOR = "|"
        private const val MEMBER_ID_WIDTH = 20

        fun from(
            memberId: Long,
            requestedAt: Instant,
        ): EnrollmentWaitlistRedisValue = EnrollmentWaitlistRedisValue(
            memberId = memberId,
            requestedAt = requestedAt,
        )

        fun deserialize(value: String): EnrollmentWaitlistRedisValue {
            val parts = value.split(SEPARATOR, limit = 2)
            return EnrollmentWaitlistRedisValue(
                memberId = parts[0].toLong(),
                requestedAt = Instant.parse(parts[1]),
            )
        }
    }
}
