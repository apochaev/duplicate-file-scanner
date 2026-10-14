import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    application
    id("org.springframework.boot") version "4.1.1"
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(project(":file-scanner"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jackson")

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.springframework.boot:spring-boot-test")
}

application {
    mainClass = "us.pochaev.dfs.cli.Main"
    // The scanner calls native code on Windows (DFS-026): :cli:run and the start scripts allow it.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.bootJar {
    // Same for `java -jar` (DFS-026.AC-7).
    manifest {
        attributes("Enable-Native-Access" to "ALL-UNNAMED")
    }
}

// NativeAccessTest reads the bootJar's manifest from the path in system property dfs.bootJar.
val bootJarFile = tasks.bootJar.flatMap { it.archiveFile }
tasks.test {
    dependsOn(tasks.bootJar)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Ddfs.bootJar=" + bootJarFile.get().asFile.absolutePath)
    })
}
