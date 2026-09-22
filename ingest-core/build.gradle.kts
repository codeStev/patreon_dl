plugins {
    `java-library`
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))

    // api, not implementation: DownloadSourceRepository et al. ARE
    // JpaRepository<...> as part of ingest-core's public contract (see the
    // design doc's "Layering" section - no mapper layer between domain and
    // persistence) - consumers need JpaRepository's inherited methods and
    // @Transactional visible on their own compile classpath too.
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    // Boot 4 split FlywayAutoConfiguration itself out of spring-boot-autoconfigure
    // into this dedicated artifact - flyway-core/flyway-database-postgresql alone
    // (the Flyway library) don't pull in Spring's integration glue for it.
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("jakarta.mail:jakarta.mail-api:2.1.3")
    runtimeOnly("org.eclipse.angus:angus-mail:2.0.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("com.icegreen:greenmail:2.1.3")
    testImplementation("com.icegreen:greenmail-junit5:2.1.3")
}
