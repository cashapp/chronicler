package com.squareup.cash.chronicler.player.miskds

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.security.KeyStore

class MiskDatasourceConfigFile(file: File) {
  companion object {
    val mapper = ObjectMapper(YAMLFactory())
      .setPropertyNamingStrategy(PropertyNamingStrategies.SnakeCaseStrategy())
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
      .registerKotlinModule()
  }

  private val tree = mapper.readTree(file)

  fun parseConfig(cluster: String, type: String) =
    tree
      .get("data_source_clusters")
      .get(cluster)
      .get(type)
      .traverse()
      .let { mapper.readValue<MiskDatasourceConfig>(it) }
}

data class MiskDatasourceConfig(
  val database: String,
  val type: DbType,
  val host: String,
  val port: Int?,
  val username: String,
  val password: String?,
  val trustCertificateKeyStoreURL: String?,
  val trustCertificateKeyStorePassword: String?,
  val clientCertificateKeyStoreURL: String?,
  val clientCertificateKeyStorePassword: String?
) {
  val trustCertificateKeyStore: KeyStore? by lazy {
    if (trustCertificateKeyStoreURL == null) {
      null
    } else {
      check(trustCertificateKeyStoreURL.startsWith("file://")) {
        "trustCertificateKeyStoreURL should start with file://"
      }.run {
        val file = File(trustCertificateKeyStoreURL.substringAfter("file://"))
        val protection = KeyStore.PasswordProtection(trustCertificateKeyStorePassword!!.toCharArray())
        KeyStore.Builder.newInstance(file, protection).keyStore
      }
    }
  }

  val clientCertificateKeyStore: KeyStore? by lazy {
    if (clientCertificateKeyStoreURL == null) {
      null
    } else {
      check(clientCertificateKeyStoreURL.startsWith("file://")) {
        "clientCertificateKeyStoreURL should start with file://"
      }.run {
        val file = File(clientCertificateKeyStoreURL.substringAfter("file://"))
        val protection = KeyStore.PasswordProtection(clientCertificateKeyStorePassword!!.toCharArray())
        KeyStore.Builder.newInstance(file, protection).keyStore
      }
    }
  }

  enum class DbType {
    MYSQL, TIDB
  }
}
