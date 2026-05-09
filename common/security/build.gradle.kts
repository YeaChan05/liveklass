dependencies {
    api("org.springframework.boot:spring-boot-starter-security")
    implementation(project(":common:api"))
    implementation(project(":common:exception"))
    testImplementation(project(":course:api"))
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(rootProject.libs.jjwt.api)
    testImplementation(rootProject.libs.jjwt.impl)
    testImplementation(rootProject.libs.jjwt.jackson)
}
