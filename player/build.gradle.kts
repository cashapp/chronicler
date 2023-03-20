import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  application
  kotlin("jvm")
  id("com.github.johnrengelman.shadow") version "8.1.1"
  id("chronicler.publish")
}

dependencies {
  with(chronicler.Dependencies) {
    implementation(kotlinReflection)
    implementation(kotlinStdLibJdk8)
    implementation(kotlinxCoroutines)
    implementation(kotlinxCoroutinesJdk8)

    implementation(logbackClassic)
    implementation(logstashLogbackEncoder)
    implementation(metricsCore)
    implementation(prometheusClient)
    implementation(prometheusHotspot)
    implementation(prometheusHttpserver)
    implementation(prometheusDropwizard)

    implementation(clikt)
    implementation(jacksonDatabind)
    implementation(jacksonDataformatYaml)
    implementation(jacksonJsr310)
    implementation(jacksonKotlin)

    implementation(awsKinesis)
    implementation(awsSts2)

    implementation(vertxMysqlClient)
    implementation(vertxKotlinCoroutines)
    implementation(nettyTcNative)

    implementation(project(":proto"))

    testImplementation(junitApi)
    testImplementation(kotlinTest)
    testImplementation(mockk)
    testRuntimeOnly(junitEngine)
  }
}

application {
  mainClass.set("app.cash.chronicler.player.MainKt")
  mainClassName = mainClass.get() //Backward compatibility, shadowJar does not like mainClass
}

val jar by tasks.getting(Jar::class) {
  archiveBaseName.set("chronicler-player")
}

val shadowJar by tasks.getting(ShadowJar::class) {
  mergeServiceFiles()
}

kotlin {
  sourceSets.all {
    languageSettings {
      optIn("kotlin.time.ExperimentalTime")
      optIn("kotlinx.coroutines.ObsoleteCoroutinesApi")
      optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
  }
}

apply<Chronicler_publish_gradle.ChroniclerPublishPlugin>()