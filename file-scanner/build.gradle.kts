plugins {
    `java-library`
}

tasks.test {
    // ModuleExportsTest reads module-info.class from the compiled main classes
    systemProperty("scanner.main.classes", sourceSets.main.get().output.classesDirs.singleFile.absolutePath)
}
