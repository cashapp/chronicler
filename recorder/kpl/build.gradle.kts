plugins {
  kotlin("jvm")
  id("com.github.johnrengelman.shadow") version "7.1.2"
  id("chronicler.publish")
}

dependencies {
  with(chronicler.Dependencies) {
    implementation(kotlinReflection)
    implementation(kotlinStdLibJdk8)
    implementation(metricsCore)
    implementation(awsKinesisProducer)
    implementation(log4j)

    implementation(project(":recorder:core"))
    implementation(project(":proto"))

    testImplementation(junitApi)
    testImplementation(kotlinTest)
    testImplementation(mockk)
    testRuntimeOnly(junitEngine)
  }
}

val jar by tasks.getting(Jar::class) {
  archiveBaseName.set("chronicler-recorder-kpl")
}

apply<Chronicler_publish_gradle.ChroniclerPublishPlugin>()