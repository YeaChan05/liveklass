dependencies {
//    implementation(project(":common:repository-jdbc"))
    implementation(project(":course:exception"))
    implementation(project(":course:infrastructure"))
    implementation(project(":course:schema"))
    integrationTestImplementation("org.springframework.boot:spring-boot-jdbc-test")
    integrationTestImplementation("org.testcontainers:testcontainers-jdbc:${libs.versions.testcontainers.get()}")
    integrationTestImplementation("org.testcontainers:testcontainers-mysql:${libs.versions.testcontainers.get()}")
    integrationTestRuntimeOnly("com.mysql:mysql-connector-j")
}
