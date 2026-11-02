import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    application
    id("org.springframework.boot") version "4.1.1"
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(project(":file-scanner"))
    implementation(project(":scanner-spring"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jackson")

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.springframework.boot:spring-boot-test")
}

application {
    mainClass = "us.pochaev.dfs.cli.Main"
    // installBootDist writes bin/dfs and bin/dfs.bat beside the server's dfs-server (DFS-043 decision 2).
    applicationName = "dfs"
    // The scanner calls native code on Windows (DFS-026): :cli:run and the start scripts allow it.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.bootJar {
    // Same for `java -jar` (DFS-026.AC-7).
    manifest {
        attributes("Enable-Native-Access" to "ALL-UNNAMED")
    }
}

// NativeAccessTest reads the bootJar's manifest (dfs.bootJar); LaunchScriptTest runs the installed start script
// (dfs.installDir, DFS-043 decision 3).
val bootJarFile = tasks.bootJar.flatMap { it.archiveFile }
val installDir = layout.buildDirectory.dir("install/dfs-boot")
tasks.test {
    dependsOn(tasks.bootJar, tasks.named("installBootDist"))
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Ddfs.bootJar=" + bootJarFile.get().asFile.absolutePath,
            "-Ddfs.installDir=" + installDir.get().asFile.absolutePath,
        )
    })
}
