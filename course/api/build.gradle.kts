dependencies {
    implementation(project(":common:api"))
    implementation(project(":common:exception"))
    implementation(project(":common:security"))
    implementation(project(":course:exception"))
    implementation(project(":course:service"))
    testImplementation("org.springframework.security:spring-security-test")
}
