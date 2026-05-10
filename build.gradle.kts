
import com.linecorp.support.project.multi.recipe.configureByTypeExpression
import com.linecorp.support.project.multi.recipe.configureByTypeHaving
import com.linecorp.support.project.multi.recipe.configureByTypePrefix
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    idea
    java
    `java-library`
    `jvm-test-suite`
    jacoco
    alias(libs.plugins.ktlint)
    alias(libs.plugins.build.recipe)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
}

val developmentOnly by configurations.creating
configurations {
    runtimeClasspath {
        extendsFrom(developmentOnly)
    }
}

allprojects {
    findProperty("group")?.let {
        group = it
    }
}

ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
        exclude("**/out/**")
    }
}

configureByTypePrefix("kotlin") {
    apply {
        plugin("java-library")
        plugin("org.jetbrains.kotlin.jvm")
        plugin("jacoco")
    }

    val hasIntegrationTestSources = file("src/integrationTest/kotlin").exists() ||
        file("src/integrationTest/resources").exists()

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    testing {
        suites {
            val test by getting(JvmTestSuite::class)
            if (hasIntegrationTestSources) {
                register<JvmTestSuite>("integrationTest") {
                    sources {
                        java {
                            setSrcDirs(listOf("src/integrationTest/kotlin"))
                        }
                        resources {
                            setSrcDirs(listOf("src/integrationTest/resources"))
                        }
                    }
                }
            }

            withType<JvmTestSuite> {
                useJUnitJupiter()

                targets {
                    all {
                        dependencies {
                            implementation(project())
                        }
                        testTask.configure {
                            testLogging {
                                events = mutableSetOf(TestLogEvent.FAILED)
                                exceptionFormat = TestExceptionFormat.FULL
                            }
                        }
                    }
                }
            }
        }
    }

    if (hasIntegrationTestSources) {
        val integrationTestImplementation by configurations.getting {
            extendsFrom(configurations.testImplementation.get())
        }

        val integrationTestRuntimeOnly by configurations.getting {
            extendsFrom(configurations.testRuntimeOnly.get())
        }

        val integrationTestCompileOnly by configurations.getting
        val integrationTestAnnotationProcessor by configurations.getting

        tasks {
            val integrationTest by getting
            val check by getting {
                dependsOn("integrationTest")
            }
        }

        tasks.named("integrationTest") {
            finalizedBy("jacocoIntegrationTestReport")
        }

        tasks.register<JacocoReport>("jacocoIntegrationTestReport") {
            dependsOn("integrationTest")
            executionData(layout.buildDirectory.file("jacoco/integrationTest.exec"))
            sourceDirectories.from(files("src/main/kotlin"))
            classDirectories.from(layout.buildDirectory.dir("classes/kotlin/main"))
        }
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    dependencies {
        implementation(rootProject.libs.jspecify)
        implementation(rootProject.libs.kotlin.logging)
        testImplementation(enforcedPlatform(SpringBootPlugin.BOM_COORDINATES))
        testImplementation("org.springframework.boot:spring-boot-starter-test")
    }
}

configureByTypePrefix("dependencies") {
    dependencies {
        implementation(enforcedPlatform(SpringBootPlugin.BOM_COORDINATES))
    }
}

configureByTypeHaving("boot") {
    dependencies {
        implementation(enforcedPlatform(SpringBootPlugin.BOM_COORDINATES))
        implementation("org.springframework.boot:spring-boot-starter")
        implementation(rootProject.libs.kotlin.reflect)
    }
}

configureByTypeHaving("kotlin", "boot") {
    apply {
        plugin("org.springframework.boot")
        plugin("org.jetbrains.kotlin.plugin.spring")
    }
}

configureByTypeHaving("boot", "mvc") {
    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-security")
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springframework.boot:spring-boot-starter-validation")
    }
}

configureByTypeHaving("boot", "jpa", "repository") {
    apply {
        plugin("org.jetbrains.kotlin.plugin.jpa")
    }
    extensions.configure<AllOpenExtension> {
        annotation("jakarta.persistence.Entity")
        annotation("jakarta.persistence.Embeddable")
        annotation("jakarta.persistence.MappedSuperclass")
    }

    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    }
}

configureByTypeHaving("boot", "application") {
    apply {
        plugin("org.springframework.boot")
    }

    dependencies {
    }

    tasks.withType<BootRun>().configureEach {
        workingDir = rootProject.projectDir
    }
}

configureByTypeHaving("mvc", "application") {
    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-web")
    }
}

// bootJar -> application only
configureByTypeExpression("^(?!.*application).*$") {
    tasks.withType<BootJar> {
        enabled = false
    }
}

subprojects {
    apply {
        plugin("org.jlleitschuh.gradle.ktlint")
    }

    // configure package path by project name
    afterEvaluate {
        val projectType = findProperty("type")?.toString().orEmpty()
        val isApplicationModule = projectType.contains("application")
        if (!isApplicationModule) {
            tasks.withType(Jar::class.java).configureEach {
                archiveBaseName.set(project.path.trimStart(':').replace(':', '-'))
            }
        }
    }
}
val jacocoExcludes = listOf(
    "**/*Application*",
    "**/model/**",
    "**/*Dtos*",
    "**/*Request*",
    "**/*Response*",
    "**/exception/**",
    "**/generated/**",
    "**/*Config*",
)

fun Project.jacocoMainClassDirectories() = fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
    exclude(jacocoExcludes)
}

tasks.register<JacocoReport>("jacocoRootReport") {
    dependsOn(subprojects.map { it.tasks.withType<Test>() })

    executionData(
        subprojects.flatMap {
            listOf(
                it.layout.buildDirectory.file("jacoco/test.exec"),
                it.layout.buildDirectory.file("jacoco/integrationTest.exec"),
            )
        },
    )

    sourceDirectories.from(
        subprojects.map { it.file("src/main/kotlin") },
    )

    classDirectories.setFrom(
        files(
            subprojects.map { it.jacocoMainClassDirectories() },
        ),
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoRootCoverageVerification") {
    dependsOn("jacocoRootReport")

    executionData(
        subprojects.flatMap {
            listOf(
                it.layout.buildDirectory.file("jacoco/test.exec"),
                it.layout.buildDirectory.file("jacoco/integrationTest.exec"),
            )
        },
    )

    sourceDirectories.from(
        subprojects.map { it.file("src/main/kotlin") },
    )

    classDirectories.setFrom(
        files(
            subprojects.map { it.jacocoMainClassDirectories() },
        ),
    )

    violationRules {
        rule {
            enabled = true

            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }

            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("test") {
    dependsOn("jacocoRootCoverageVerification")
}
