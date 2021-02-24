apply(plugin = "kotlin")
apply(plugin = "com.github.johnrengelman.shadow")

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
