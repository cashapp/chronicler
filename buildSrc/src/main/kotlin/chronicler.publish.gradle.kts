class ChroniclerPublishPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val SONATYPE_NEXUS_USERNAME: String? by target
    val SONATYPE_NEXUS_PASSWORD: String? by target

    val GROUP: String by target
    val VERSION_NAME: String by target
    val POM_ARTIFACT_NAME: String by target
    val POM_NAME: String by target
    val POM_DESCRIPTION: String by target

    val signingKey: String? by target

    target.apply<MavenPublishPlugin>()

    target.extensions.configure<PublishingExtension> {
      publications {
        create<MavenPublication>("maven") {
          artifactId = POM_ARTIFACT_NAME
          groupId = GROUP
          version = VERSION_NAME

          from(target.components["java"])

          pom {
            packaging = "jar"

            name.set(POM_NAME)
            description.set(POM_DESCRIPTION)
            url.set("https://github.com/cashapp/chronicler.git")

            licenses {
              license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
              }
            }
            developers {
              developer {
                id.set("bezmax")
                name.set("Maksim Bezsaznyj")
                email.set("bezmax@gmail.com")
                organization.set("Block Inc.")
              }
              developer {
                id.set("jontirsen")
                name.set("Jon Tirsen")
                email.set("jontirsen@squareup.com")
                organization.set("Block Inc.")
              }
            }
            scm {
              connection.set("scm:git:git://github.com/cashapp/chronicler.git")
              developerConnection.set("scm:git:ssh://github.com/cashapp/chronicler.git")
              url.set("https://github.com/cashapp/chronicler")
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
