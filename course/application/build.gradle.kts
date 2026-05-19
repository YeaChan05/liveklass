dependencies {
    // common
    implementation(project(":common:api"))
    implementation(project(":common:security"))

    // internal
    implementation(project(":course:api"))
    implementation(project(":course:repository-jpa"))
    implementation(project(":course:repository-jdbc"))
    implementation(project(":course:repository-redis"))
    implementation(project(":course:schema"))

    runtimeOnly("com.mysql:mysql-connector-j") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // spring boot dev tools
    runtimeOnly("org.springframework.boot:spring-boot-docker-compose")

    // observability
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // spring web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
