plugins {
  kotlin("jvm")
  id("com.github.johnrengelman.shadow") version "7.1.2"
  id("chronicler.publish")
}

dependencies {
  with(chronicler.Dependencies) {
    api(mysql)

    implementation(kotlinReflection)
    implementation(kotlinStdLibJdk8)

    implementation(project(":proto"))

    testImplementation(junitApi)
    testImplementation(kotlinTest)
    testImplementation(mockk)
    testRuntimeOnly(junitEngine)
  }
}

val jar by tasks.getting(Jar::class) {
  archiveBaseName.set("chronicler-recorder-core")
}

apply<Chronicler_publish_gradle.ChroniclerPublishPlugin>()