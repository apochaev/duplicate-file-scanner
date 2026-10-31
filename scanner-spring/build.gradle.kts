// The scanner's shared Spring wiring (DFS-031): an autoconfigure library, not an application, so it applies
// java-library and Spring Boot's BOM but not the Boot plugin (DFS-031 decision 5).
plugins {
    `java-library`
}

dependencies {
    // Keep this version in step with cli's org.springframework.boot plugin.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    api(project(":file-scanner"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    testImplementation("org.springframework.boot:spring-boot-test")
    // ApplicationContextRunner's AssertableApplicationContext needs AssertJ on the classpath.
    testImplementation("org.assertj:assertj-core")
}
