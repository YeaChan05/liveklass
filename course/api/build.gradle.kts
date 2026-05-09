dependencies {
    implementation(project(":common:api"))
    implementation(project(":common:exception"))
    implementation(project(":common:security"))
    implementation(project(":course:exception"))
    implementation(project(":course:service"))
    implementation(rootProject.libs.jjwt.api)
    implementation(rootProject.libs.jjwt.impl)
    implementation(rootProject.libs.jjwt.jackson)
    testImplementation("org.springframework.security:spring-security-test")
}
