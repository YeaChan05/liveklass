package org.yechan.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.yechan.TokenGenerator
import tools.jackson.databind.ObjectMapper
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
)

@Component
@ConditionalOnProperty(
    prefix = "load-test.member-token-generator",
    name = ["enabled"],
    havingValue = "true",
)
class MemberTokenGenerator(
    private val jdbcTemplate: JdbcTemplate,
    private val tokenGenerator: TokenGenerator,
    private val objectMapper: ObjectMapper,
    private val properties: MemberTokenGeneratorProperties,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        validateProperties()

        val members = createSeedMembers()
        batchInsertMembers(members)

        val tokens = members.map { member ->
            val token = tokenGenerator.generate(
                memberId = member.id,
                roles = setOf(properties.role),
            )

            token.accessToken
        }

        writeTokens(tokens)

        println(
            """
            MemberTokenGenerator completed.
            count=${tokens.size}
            outputPath=${properties.outputPath}
            """.trimIndent(),
        )
    }

    private fun validateProperties() {
        require(properties.count > 0) {
            "load-test.member-token-generator.count must be greater than 0"
        }

        require(properties.outputPath.isNotBlank()) {
            "load-test.member-token-generator.output-path must not be blank"
        }
    }

    private fun createSeedMembers(): List<SeedMember> {
        val baseId = System.currentTimeMillis() * 100_000

        return (0 until properties.count).map { index ->
            SeedMember(
                id = baseId + index,
                email = "${properties.emailPrefix}-$index@${properties.emailDomain}",
                passwordHash = properties.passwordHash,
                name = "k6 테스트 수강생-$index",
                role = properties.role,
                status = properties.status,
            )
        }
    }

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
            ON DUPLICATE KEY UPDATE
                password_hash = VALUES(password_hash),
                name = VALUES(name),
                role = VALUES(role),
                status = VALUES(status),
                updated_at = VALUES(updated_at)
            """.trimIndent(),
            members,
            1_000,
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

    private fun writeTokens(tokens: List<String>) {
        val outputPath = Path.of(properties.outputPath)
        val parent = outputPath.parent

        if (parent != null) {
            Files.createDirectories(parent)
        }

        objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValue(outputPath.toFile(), tokens)
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
