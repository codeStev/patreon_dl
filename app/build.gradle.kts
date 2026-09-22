plugins {
    java
    id("org.springframework.boot") version "4.1.1"
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))

    implementation(project(":ingest-core"))
    implementation(project(":ingest-providers-default"))
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // spring-boot-starter-test marks this optional (so non-web projects
    // don't pull in web-test machinery) - needed explicitly now that this
    // module has spring-boot-starter-web.
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:postgresql:1.21.3")
}
