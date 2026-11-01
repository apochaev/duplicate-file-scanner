import org.springframework.boot.gradle.plugin.SpringBootPlugin
import com.github.gradle.node.npm.task.NpmTask

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

// Everything the frontend build reads, so an unchanged frontend is not rebuilt (DFS-037.AC-5).
val frontendSources = fileTree("frontend") {
    // src/app/api is generated from openapi.json inside these tasks (DFS-038), so it is not an input.
    exclude("node_modules/**", "dist/**", ".angular/**", "src/app/api/**")
}

val ngBuild by tasks.registering(NpmTask::class) {
    description = "Builds the Angular app into frontend/dist."
    dependsOn(tasks.npmInstall)
    npmCommand = listOf("run", "build")
    inputs.files(frontendSources).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("frontendSources")
    outputs.dir("frontend/dist/frontend/browser").withPropertyName("browserBundle")
}

// The built app becomes static/ on the classpath, so the jar, bootRun and the tests serve it (DFS-037 decision 2).
val frontendResources by tasks.registering(Sync::class) {
    description = "Copies the Angular build into build/generated/frontend/static."
    from(ngBuild) { into("static") }
    into(layout.buildDirectory.dir("generated/frontend"))
}
sourceSets.main { resources.srcDir(frontendResources) }

val npmTest by tasks.registering(NpmTask::class) {
    description = "Runs the Angular unit tests (Vitest)."
    group = "verification"
    dependsOn(tasks.npmInstall)
    npmCommand = listOf("test", "--", "--watch=false")
    inputs.files(frontendSources).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("frontendSources")
    val passed = layout.buildDirectory.file("frontend-test/passed")
    outputs.file(passed).withPropertyName("passedMarker")
    doLast { passed.get().asFile.writeText("ok\n") }
}
tasks.check { dependsOn(npmTest) }

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(project(":file-scanner"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // The OpenAPI spec from the controllers, served only when switched on (DFS-038 decisions 1 and 3).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0")

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
// The committed OpenAPI spec (DFS-038 decision 1).
val openApiFile = layout.projectDirectory.file("frontend/openapi.json")
tasks.test {
    dependsOn(tasks.bootJar, tasks.named("installBootDist"))
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Ddfs.bootJar=" + bootJarFile.get().asFile.absolutePath,
            "-Ddfs.installDir=" + installDir.get().asFile.absolutePath,
            "-Ddfs.openapi.file=" + openApiFile.asFile.absolutePath,
        )
    })
    // OpenApiSpecTest compares the live spec with this file, so a changed file reruns the tests (DFS-038).
    inputs.files(openApiFile).withPropertyName("openApiSpec").withPathSensitivity(PathSensitivity.RELATIVE)
}

// Rewrites frontend/openapi.json from the controllers: OpenApiSpecTest in write mode (DFS-038 decision 1).
val updateOpenApi by tasks.registering(Test::class) {
    description = "Writes the OpenAPI spec the controllers produce to frontend/openapi.json."
    group = "build"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("us.pochaev.dfs.server.OpenApiSpecTest.committedSpecMatchesTheControllers") }
    systemProperty("dfs.openapi.update", "true")
    systemProperty("dfs.openapi.file", openApiFile.asFile.absolutePath)
    outputs.upToDateWhen { false }
}
