plugins {
    application
}

dependencies {
    implementation(project(":file-scanner"))
}

application {
    mainClass = "us.pochaev.dfs.cli.Main"
}
