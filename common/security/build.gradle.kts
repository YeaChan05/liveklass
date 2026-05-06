dependencies {
    implementation(project(":common:api"))
    implementation(project(":common:exception"))
    testImplementation("org.springframework.security:spring-security-test")
    implementation(rootProject.libs.jjwt.api)
    implementation(rootProject.libs.jjwt.impl)
    implementation(rootProject.libs.jjwt.jackson)
}
