dependencies {
    api(project(":course:model"))
    implementation(project(":common:exception"))
    implementation(project(":common:security"))
    implementation(project(":course:infrastructure"))
    implementation(project(":course:exception"))
    implementation("org.springframework:spring-tx")
    implementation("io.hypersistence:hypersistence-utils-hibernate-71:3.14.1")
}
