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
        val targets = courseIds.toTargets().ifEmpty { return }

        val caseSql = targets.caseSql()
        val inSql = targets.inSql()

        val sql = reserveSeatsBulkSql(
            caseSql = caseSql,
            inSql = inSql,
        )

        val params = buildList {
            addAll(targets.caseParams())
            addAll(targets.inParams())
            add(CourseStatus.OPEN.name)
            addAll(targets.caseParams())
        }

        val updatedCount = jdbcTemplate.update(sql, *params.toTypedArray())

        if (updatedCount != targets.size) {
            throw CourseInvalidStateException(
                "강좌 좌석 예약에 실패했습니다. expected=${targets.size}, actual=$updatedCount",
            )
        }
    }

    @Transactional
    override fun releaseSeatsBulk(courseIds: Map<Long, Int>) {
        val targets = courseIds.toTargets().ifEmpty { return }

        val caseSql = targets.caseSql()
        val inSql = targets.inSql()

        val sql = releaseSeatsBulkSql(
            caseSql = caseSql,
            inSql = inSql,
        )

        val params = buildList {
            addAll(targets.caseParams())
            addAll(targets.inParams())
            addAll(targets.caseParams())
        }

        val updatedCount = jdbcTemplate.update(sql, *params.toTypedArray())

        if (updatedCount != targets.size) {
            throw CourseInvalidStateException(
                "강좌 좌석 반환에 실패했습니다. expected=${targets.size}, actual=$updatedCount",
            )
        }
    }

    @Language("SQL")
    private fun reserveSeatsBulkSql(
        caseSql: String,
        inSql: String,
    ): String =
        """
    UPDATE courses
    SET seat_left_count =
        seat_left_count -
        CASE id
            $caseSql
            ELSE 0
        END
    WHERE id IN ($inSql)
      AND status = ?
      AND seat_left_count >=
        CASE id
            $caseSql
            ELSE 0
        END
        """.trimIndent()

    @Language("SQL")
    private fun releaseSeatsBulkSql(
        caseSql: String,
        inSql: String,
    ): String =
        """
    UPDATE courses
    SET seat_left_count =
        seat_left_count +
        CASE id
            $caseSql
            ELSE 0
        END
    WHERE id IN ($inSql)
      AND seat_left_count +
        CASE id
            $caseSql
            ELSE 0
        END <= capacity
        """.trimIndent()

    private fun Map<Long, Int>.toTargets(): List<Pair<Long, Int>> = filterValues { it > 0 }
        .map { (courseId, count) -> courseId to count }

    private fun List<Pair<Long, Int>>.caseSql(): String = joinToString(separator = "\n") {
        "WHEN ? THEN ?"
    }

    private fun List<Pair<Long, Int>>.inSql(): String = joinToString(separator = ", ") {
        "?"
    }

    private fun List<Pair<Long, Int>>.caseParams(): List<Any> = flatMap { (courseId, count) ->
        listOf(courseId, count)
    }

    private fun List<Pair<Long, Int>>.inParams(): List<Any> = map { (courseId, _) -> courseId }
}
