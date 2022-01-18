package app.cash.chronicler.player.miskds

import io.vertx.core.net.KeyCertOptions
import io.vertx.core.net.TrustOptions
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.mysqlclient.SslMode
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager

fun MiskDatasourceConfig.toMySQLConnectOptions() = let { misk ->
  MySQLConnectOptions().also { opt ->
    opt.host = misk.host
    opt.database = misk.database
    opt.user = misk.username
    if (misk.password != null) {
      opt.password = misk.password
    }
    opt.port = when (misk.type) {
      MiskDatasourceConfig.DbType.MYSQL -> 3306
      MiskDatasourceConfig.DbType.TIDB -> 4000
    }
    if (misk.clientCertificateKeyStore != null && misk.trustCertificateKeyStore != null) {
      val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(misk.trustCertificateKeyStore) }
        .trustManagers.single()

      val keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        .apply { init(misk.clientCertificateKeyStore, misk.clientCertificateKeyStorePassword!!.toCharArray()) }
        .keyManagers.single()

      opt.sslMode = SslMode.VERIFY_CA
      opt.trustOptions = TrustOptions.wrap(trustManager)
      opt.keyCertOptions = KeyCertOptions.wrap(keyManager as X509KeyManager)
    }
  }
}
