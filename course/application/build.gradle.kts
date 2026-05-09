dependencies {
    // common
    implementation(project(":common:api"))
    implementation(project(":common:security"))

    // internal
    implementation(project(":course:api"))
    implementation(project(":course:repository-jpa"))
    implementation(project(":course:repository-redis"))
    implementation(project(":course:schema"))

    runtimeOnly("com.mysql:mysql-connector-j") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // spring boot dev tools
    runtimeOnly("org.springframework.boot:spring-boot-docker-compose")

    // spring liquibase org.liquibase:liquibase-gradle-plugin
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
}
