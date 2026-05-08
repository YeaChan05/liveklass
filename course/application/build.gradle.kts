dependencies {
    // common
    implementation(project(":common:api"))
    implementation(project(":common:security"))

    // internal
    implementation(project(":course:api"))
    implementation(project(":course:repository-jpa"))
    implementation(project(":course:repository-redis"))
    implementation(project(":course:schema"))

    compileOnly("com.mysql:mysql-connector-j") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // spring boot dev tools
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // test
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:testcontainers-jdbc")
}
