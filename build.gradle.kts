plugins {
    java
}

group = "com.github.meiiraru"
version = "1.1.0"
val mainClass = "segypng.Main"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.thecoldwine", "sigrun", "sigrun-0.4.4")
}

tasks.register<Jar>("fatJar") {
    //archiveClassifier.set(os)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    manifest.attributes["Main-Class"] = mainClass

    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.jar {
    archiveClassifier.set("")
    manifest.attributes["Main-Class"] = mainClass
    from("LICENSE.md")
}