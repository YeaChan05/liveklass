dependencies {
    api(project(":course:model"))
    implementation(project(":common:exception"))
    implementation(project(":common:security"))
    implementation(project(":course:infrastructure"))
    implementation(project(":course:exception"))
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework:spring-tx")
}
