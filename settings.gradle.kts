rootProject.name = "liveklass"
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("course:application")
include(":course:api")
include(":course:exception")
include(":course:infrastructure")
include(":course:model")
include(":course:repository-jpa")
include(":course:repository-jdbc")
include(":course:repository-redis")
include(":course:service")
include(":course:schema")

include(":common:api")
include(":common:boot")
include(":common:exception")
include(":common:model")
include(":common:repository-jpa")
include(":common:security")
