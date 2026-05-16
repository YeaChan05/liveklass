package org.yechan.enrollment

import org.intellij.lang.annotations.Language
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Duration
import java.time.Instant

class EnrollmentWaitlistRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val ttl: Duration,
) : EnrollmentWaitlistRepository {
    override fun enqueue(
        courseId: Long,
        memberId: Long,
        requestedAt: Instant,
    ) {
        redisTemplate.execute(
            ENQUEUE_SCRIPT,
            listOf(
                EnrollmentWaitlistRedisKey.byCourseId(courseId).value,
                EnrollmentWaitlistRedisKey.courseIds().value,
            ),
            memberId.toString(),
            requestedAt.toEpochMilli().toString(),
            ttl.toMillis().toString(),
            courseId.toString(),
        )
    }

    override fun pop(courseId: Long): EnrollmentWaitlistEntry? {
        val key = EnrollmentWaitlistRedisKey.byCourseId(courseId)
        val popped = redisTemplate.execute(
            POP_AND_CLEANUP_SCRIPT,
            listOf(
                key.value,
                EnrollmentWaitlistRedisKey.courseIds().value,
                EnrollmentWaitlistRedisKey.soldOut(courseId).value,
            ),
            courseId.toString(),
            ttl.toMillis().toString(),
        ).orEmpty()

        if (popped.size < 2) {
            return null
        }

        val memberId = popped[0].toString().toLongOrNull() ?: return null
        val requestedAt = popped[1].toString().toDoubleOrNull()?.toLong() ?: return null

        return EnrollmentWaitlistEntry(
            courseId = courseId,
            memberId = memberId,
            requestedAt = Instant.ofEpochMilli(requestedAt),
        )
    }

    override fun findByMemberId(memberId: Long): List<EnrollmentWaitlistEntry> = findCourseIds()
        .flatMap { courseId ->
            redisTemplate.opsForZSet()
                .rangeWithScores(EnrollmentWaitlistRedisKey.byCourseId(courseId).value, 0, -1)
                .orEmpty()
                .mapNotNull { entry ->
                    val currentMemberId = entry.value?.toLongOrNull() ?: return@mapNotNull null
                    if (currentMemberId != memberId) {
                        return@mapNotNull null
                    }

                    EnrollmentWaitlistEntry(
                        courseId = courseId,
                        memberId = currentMemberId,
                        requestedAt = Instant.ofEpochMilli(
                            entry.score?.toLong() ?: return@mapNotNull null,
                        ),
                    )
                }
        }
        .filter { it.memberId == memberId }
        .sortedBy { it.requestedAt }

    override fun remove(
        courseId: Long,
        memberId: Long,
    ) {
        redisTemplate.execute(
            REMOVE_AND_CLEANUP_SCRIPT,
            listOf(
                EnrollmentWaitlistRedisKey.byCourseId(courseId).value,
                EnrollmentWaitlistRedisKey.courseIds().value,
                EnrollmentWaitlistRedisKey.soldOut(courseId).value,
            ),
            memberId.toString(),
            ttl.toMillis().toString(),
        )
    }

    override fun findCourseIds(): Set<Long> {
        val courseIds = redisTemplate.opsForZSet()
            .range(EnrollmentWaitlistRedisKey.courseIds().value, 0, -1)
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toCollection(linkedSetOf())

        val liveCourseIds = linkedSetOf<Long>()
        courseIds.forEach { courseId ->
            val waitlistKey = EnrollmentWaitlistRedisKey.byCourseId(courseId).value
            if (redisTemplate.hasKey(waitlistKey) == true) {
                liveCourseIds += courseId
                return@forEach
            }

            redisTemplate.opsForZSet()
                .remove(EnrollmentWaitlistRedisKey.courseIds().value, courseId.toString())
            redisTemplate.delete(EnrollmentWaitlistRedisKey.soldOut(courseId).value)
        }

        return liveCourseIds
    }

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

private val ENQUEUE_SCRIPT = DefaultRedisScript<Long>().apply {
    @Language("Lua")
    val script = """
        redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
        redis.call('PEXPIRE', KEYS[1], ARGV[3])
        redis.call('ZADD', KEYS[2], ARGV[4], ARGV[4])
        return 1
        """
    setScriptText(
        script.trimIndent(),
    )
    resultType = Long::class.java
}

private val REMOVE_AND_CLEANUP_SCRIPT = DefaultRedisScript<Long>().apply {
    @Language("Lua")
    val script = """
        redis.call('ZREM', KEYS[1], ARGV[1])
        if redis.call('ZCARD', KEYS[1]) == 0 then
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], string.match(KEYS[1], ":(%d+)$") or "")
            redis.call('DEL', KEYS[3])
        else
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            redis.call('PEXPIRE', KEYS[3], ARGV[2])
        end
        return 1
        """
    setScriptText(
        script.trimIndent(),
    )
    resultType = Long::class.java
}

private val POP_AND_CLEANUP_SCRIPT = DefaultRedisScript<List<Any>>().apply {
    @Language("Lua")
    val script = """
        local popped = redis.call('ZPOPMIN', KEYS[1], 1)
        if (popped == nil) or (#popped == 0) then
            return {}
        end
        if redis.call('ZCARD', KEYS[1]) == 0 then
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], string.match(KEYS[1], ":(%d+)$") or "")
            redis.call('DEL', KEYS[3])
        else
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            redis.call('PEXPIRE', KEYS[3], ARGV[2])
        end
        return popped
        """
    setScriptText(
        script.trimIndent(),
    )
    @Suppress("UNCHECKED_CAST")
    resultType = List::class.java as Class<List<Any>>
}
