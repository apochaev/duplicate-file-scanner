allprojects {
    group = "us.pochaev.dfs"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.0.0"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("com.tngtech.archunit:archunit:1.5.0")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // The scanner calls native code on Windows (DFS-026); allow it for the classpath (unnamed module).
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}
