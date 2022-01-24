class ChroniclerPublishPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val SONATYPE_NEXUS_USERNAME: String? by target
    val SONATYPE_NEXUS_PASSWORD: String? by target

    val GROUP: String by target
    val VERSION_NAME: String by target
    val POM_ARTIFACT_NAME: String by target

    val signingKey: String? by target

    target.apply<MavenPublishPlugin>()

    target.extensions.configure<PublishingExtension> {
      publications {
        create<MavenPublication>("maven") {
          groupId = GROUP
          artifactId = POM_ARTIFACT_NAME
          version = VERSION_NAME

          from(target.components["java"])

          pom {
            packaging = "jar"
            licenses {
              license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
              }
            }
          }
        }
      }

      repositories {
        maven("https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
          if (SONATYPE_NEXUS_USERNAME != null && SONATYPE_NEXUS_PASSWORD != null) {
            credentials {
              username = SONATYPE_NEXUS_USERNAME
              password = SONATYPE_NEXUS_PASSWORD
            }
          }
        }
      }
    }

    signingKey?.let {
      target.apply<SigningPlugin>()

      target.extensions.configure<SigningExtension> {
        useInMemoryPgpKeys(signingKey, "")
        val publication = target.extensions.getByType<PublishingExtension>().publications["maven"]
        sign(publication)
      }
    }
  }
}
