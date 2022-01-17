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
}
