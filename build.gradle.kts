repositories {
  mavenCentral()
}

buildscript {
  repositories {
    mavenCentral()
  }

  dependencies {
    with(chronicler.Dependencies) {
      classpath(kotlinGradlePlugin)
      classpath(kotlinxCoroutines)
      classpath(junitGradlePlugin)
      classpath(wireGradlePlugin)
    }
  }
}

subprojects {
  repositories {
    mavenCentral()
  }

  apply(plugin = "java")
  apply(plugin = "kotlin")

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
      jvmTarget = "11"
    }
  }

  configure<JavaPluginExtension> {
    withSourcesJar()
    withJavadocJar()
  }
}