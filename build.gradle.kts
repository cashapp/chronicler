buildscript {
  dependencies {
    with(chronicler.Dependencies) {
      classpath(kotlinGradlePlugin)
      classpath(kotlinxCoroutines)
      classpath(junitGradlePlugin)
      classpath(shadowJarPlugin)
      classpath(wireGradlePlugin)
    }
  }
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "kotlin")
}
