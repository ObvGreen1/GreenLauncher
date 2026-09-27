plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "me.obvgreen"
version = "1.0.0"

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.4"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("me.obvgreen.Launch")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
    useJUnitPlatform()
}

tasks.register<Exec>("packageExe") {
    dependsOn("installDist")
    val inputDir = layout.buildDirectory.dir("install/GreenLauncher/lib")
    val outputDir = layout.projectDirectory.dir("dist")
    val jarName = "GreenLauncher-${project.version}.jar"

    doFirst {
        val launcherDir = outputDir.dir("GreenLauncher").asFile
        if (launcherDir.exists()) {
            launcherDir.deleteRecursively()
        }
    }

    commandLine(
        "jpackage",
        "--type", "app-image",
        "--name", "GreenLauncher",
        "--input", inputDir.get().asFile.absolutePath,
        "--main-jar", jarName,
        "--main-class", "me.obvgreen.Launch",
        "--dest", outputDir.asFile.absolutePath
    )
}

tasks.register<Zip>("packageZip") {
    dependsOn("packageExe")
    from(layout.projectDirectory.dir("dist/GreenLauncher"))
    archiveFileName.set("GreenLauncher-${project.version}-windows.zip")
    destinationDirectory.set(layout.projectDirectory.dir("dist"))
}
