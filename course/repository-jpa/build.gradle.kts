dependencies {
    implementation(project(":common:repository-jpa"))
    implementation(project(":course:infrastructure"))

    integrationTestImplementation("org.springframework.boot:spring-boot-data-jpa-test")
    integrationTestImplementation("org.springframework.boot:spring-boot-jdbc-test")
    integrationTestImplementation("org.testcontainers:testcontainers-jdbc:${libs.versions.testcontainers.get()}")
    integrationTestImplementation("org.testcontainers:testcontainers-mysql:${libs.versions.testcontainers.get()}")
    integrationTestRuntimeOnly("com.mysql:mysql-connector-j")
}
