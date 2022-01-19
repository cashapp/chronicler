plugins {
  id("com.squareup.wire")
  id("chronicler.publish")
}

dependencies {
  with(chronicler.Dependencies) {
    api(wireRuntime)
  }
}

val jar by tasks.getting(Jar::class) {
  archiveBaseName.set("chronicler-proto")
}

wire {
  sourcePath {
    srcDir("src/main/proto")
  }
  kotlin {
    javaInterop = true
  }
}

sourceSets {
  val main by getting {
    resources.srcDir(listOf("src/main/proto"))
    java.srcDir(listOf("$buildDir/generated/source/wire"))
  }
}

apply<Chronicler_publish_gradle.ChroniclerPublishPlugin>()