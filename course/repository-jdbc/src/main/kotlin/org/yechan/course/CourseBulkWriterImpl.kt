package org.yechan.course

import org.intellij.lang.annotations.Language
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.yechan.enrollment.CourseBulkWriter

open class CourseBulkWriterImpl(
    private val jdbcTemplate: JdbcTemplate,
) : CourseBulkWriter {
    @Transactional
    override fun reserveSeatsBulk(courseIds: Map<Long, Int>) {
        val command = ReserveSeatsCommand.from(courseIds)

        if (command.isEmpty()) {
            return
        }

        val updatedCount = jdbcTemplate.update(
            RESERVE_SEATS_BULK_SQL,
            command.toJson(),
            CourseStatus.OPEN.name,
        )

        if (updatedCount != command.size) {
            throw CourseInvalidStateException(
                """
                강좌 예약에 실패했습니다. 
                기대: ${command.size}, 실제: $updatedCount.
                """.trimIndent() + "\n" + command.toJson(),
            )
        }
    }

    private companion object {
        @Language("SQL")
        private const val RESERVE_SEATS_BULK_SQL =
            """
            UPDATE courses c
            JOIN JSON_TABLE(
                ?,
                '$[*]' COLUMNS (
                    course_id BIGINT PATH '$.courseId',
                    quantity INT PATH '$.quantity'
                )
            ) target ON target.course_id = c.id
            SET c.seat_left_count = c.seat_left_count - target.quantity
            WHERE c.status = ?
              AND c.seat_left_count >= target.quantity
            """
    }
}

private class ReserveSeatsCommand private constructor(
    private val requests: List<ReserveSeatRequest>,
) {
    val size: Int
        get() = requests.size

    fun isEmpty(): Boolean = requests.isEmpty()

    fun toJson(): String = requests.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ",",
    ) { request ->
        """{"courseId":${request.courseId},"quantity":${request.quantity}}"""
    }

    companion object {
        fun from(courseIds: Map<Long, Int>): ReserveSeatsCommand {
            val requests = courseIds
                .asSequence()
                .filter { (_, quantity) -> quantity > 0 }
                .map { (courseId, quantity) ->
                    ReserveSeatRequest(
                        courseId = courseId,
                        quantity = quantity,
                    )
                }
                .toList()

            return ReserveSeatsCommand(requests)
        }
    }
}

private data class ReserveSeatRequest(
    val courseId: Long,
    val quantity: Int,
)
