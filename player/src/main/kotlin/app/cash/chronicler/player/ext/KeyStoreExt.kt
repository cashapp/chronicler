package app.cash.chronicler.player.ext

import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

fun KeyStore.getX509Certificates() = aliases().asSequence()
  .filter { isCertificateEntry(it) }
  .map { getCertificate(it) }
  .filterIsInstance<X509Certificate>()

fun KeyStore.getPrivateKeysWithCertChains(password: String) = aliases().asSequence()
  .filter { isKeyEntry(it) }
  .mapNotNull { alias ->
    getKey(alias, password.toCharArray())
      .takeIf { it is PrivateKey }?.let { it as PrivateKey }
      ?.let { key ->
        getCertificateChain(alias)
          .takeIf { chain -> chain.all { it is X509Certificate } }
          ?.map { it as X509Certificate }
          ?.let { chain -> key to chain }
      }
  }
