package org.yechan.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import org.yechan.TokenGenerator
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

@Configuration
@EnableConfigurationProperties(MemberTokenGeneratorProperties::class)
class MemberTokenGeneratorConfiguration

@ConfigurationProperties(prefix = "load-test.member-token-generator")
data class MemberTokenGeneratorProperties(
    var enabled: Boolean = false,
    var count: Int = 5_000,
    var outputPath: String = "./k6/tokens.json",
    var emailPrefix: String = "k6-classmate",
    var emailDomain: String = "test.com",
    var passwordHash: String = "NOT_USED_BY_K6",
    var role: String = "CLASSMATE",
    var status: String = "ACTIVE",

    // 추가
    var batchSize: Int = 10_000,
    var runId: String = System.currentTimeMillis().toString(),
)

@Component
@ConditionalOnProperty(
    prefix = "load-test.member-token-generator",
    name = ["enabled"],
    havingValue = "true",
)
class MemberTokenGenerator(
    private val jdbcTemplate: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
    private val tokenGenerator: TokenGenerator,
    private val properties: MemberTokenGeneratorProperties,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        validateProperties()

        val startedAt = System.currentTimeMillis()
        val outputPath = Path.of(properties.outputPath)
        outputPath.parent?.let(Files::createDirectories)

        val baseId = System.currentTimeMillis() * 100_000
        var generatedCount = 0

        Files.newBufferedWriter(outputPath).use { writer ->
            writer.write("[")
            var first = true

            while (generatedCount < properties.count) {
                val chunkStart = generatedCount
                val chunkEnd = minOf(chunkStart + properties.batchSize, properties.count)

                val members = (chunkStart until chunkEnd).map { index ->
                    SeedMember(
                        id = baseId + index,
                        email = seedEmail(index),
                        passwordHash = properties.passwordHash,
                        name = "k6 테스트 수강생-$index",
                        role = properties.role,
                        status = properties.status,
                    )
                }

                transactionTemplate.executeWithoutResult {
                    batchInsertMembers(members)
                }

                for (member in members) {
                    val accessToken =
                        tokenGenerator
                            .generate(
                                member.id,
                                setOf(properties.role),
                            )
                            .accessToken

                    if (!first) {
                        writer.write(",")
                    }

                    writer.write("\n")
                    writer.write(jsonString(accessToken))
                    first = false
                }

                generatedCount = chunkEnd

                if (generatedCount % 100_000 == 0 || generatedCount == properties.count) {
                    writer.flush()
                    println("MemberTokenGenerator progress. generated=$generatedCount/${properties.count}")
                }
            }

            writer.write("\n]")
        }

        val elapsedMs = System.currentTimeMillis() - startedAt

        println(
            """
            MemberTokenGenerator completed.
            count=${properties.count}
            outputPath=${properties.outputPath}
            batchSize=${properties.batchSize}
            runId=${properties.runId}
            elapsedMs=$elapsedMs
            """.trimIndent(),
        )
    }

    private fun validateProperties() {
        require(properties.count > 0) {
            "load-test.member-token-generator.count must be greater than 0"
        }

        require(properties.batchSize > 0) {
            "load-test.member-token-generator.batch-size must be greater than 0"
        }

        require(properties.outputPath.isNotBlank()) {
            "load-test.member-token-generator.output-path must not be blank"
        }
    }

    private fun seedEmail(index: Int): String = "${properties.emailPrefix}-${properties.runId}-$index@${properties.emailDomain}"

    private fun batchInsertMembers(members: List<SeedMember>) {
        val now = LocalDateTime.now()

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO members (
                id,
                email,
                password_hash,
                name,
                role,
                status,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            members,
            members.size,
        ) { ps, member ->
            ps.setLong(1, member.id)
            ps.setString(2, member.email)
            ps.setString(3, member.passwordHash)
            ps.setString(4, member.name)
            ps.setString(5, member.role)
            ps.setString(6, member.status)
            ps.setObject(7, now)
            ps.setObject(8, now)
        }
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')

        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }

        append('"')
    }

    private data class SeedMember(
        val id: Long,
        val email: String,
        val passwordHash: String,
        val name: String,
        val role: String,
        val status: String,
    )
}
