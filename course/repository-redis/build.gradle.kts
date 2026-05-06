dependencies {
    implementation(project(":course:infrastructure"))
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    integrationTestImplementation("org.testcontainers:testcontainers:${libs.versions.testcontainers.get()}")
}
