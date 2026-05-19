dependencies {
    api("org.springframework.hateoas:spring-hateoas")
    implementation(project(":common:exception"))
    implementation("tools.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
