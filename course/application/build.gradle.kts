dependencies {
    // common
    implementation(project(":common:api"))
    implementation(project(":common:security"))

    // internal
    implementation(project(":course:api"))
    implementation(project(":course:repository-jpa"))
    implementation(project(":course:repository-redis"))
    implementation(project(":course:schema"))

    // testcontainers
    implementation("org.testcontainers:testcontainers-jdbc")
    implementation("org.testcontainers:testcontainers-mysql")
    runtimeOnly("com.mysql:mysql-connector-j") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // test
}
