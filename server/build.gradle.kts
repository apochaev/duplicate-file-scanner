import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    application
    id("org.springframework.boot") version "4.1.1"
    id("com.github.node-gradle.node") version "7.1.0"
}

// The Angular app in frontend/ (DFS-037): Gradle downloads Node under server/.gradle, nothing is installed globally.
node {
    version = "24.22.2"
    download = true
    npmInstallCommand = "ci"
    nodeProjectDir = file("frontend")
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(project(":file-scanner"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.springframework.boot:spring-boot-test")
}

application {
    mainClass = "us.pochaev.dfs.server.Main"
    // The start scripts are bin/dfs-server and bin/dfs-server.bat (DFS-035 decision 4).
    applicationName = "dfs-server"
    // The scanner calls native code on Windows (DFS-026): bootRun and the start scripts allow it.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.bootJar {
    archiveFileName = "dfs-server.jar"
    // Same for `java -jar` (DFS-035.AC-6).
    manifest {
        attributes("Enable-Native-Access" to "ALL-UNNAMED")
    }
}

// NativeAccessTest reads the jar's manifest (dfs.bootJar); LaunchScriptTest runs the installed start script (dfs.installDir).
val bootJarFile = tasks.bootJar.flatMap { it.archiveFile }
val installDir = layout.buildDirectory.dir("install/dfs-server-boot")
tasks.test {
    dependsOn(tasks.bootJar, tasks.named("installBootDist"))
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Ddfs.bootJar=" + bootJarFile.get().asFile.absolutePath,
            "-Ddfs.installDir=" + installDir.get().asFile.absolutePath,
        )
    })
}
