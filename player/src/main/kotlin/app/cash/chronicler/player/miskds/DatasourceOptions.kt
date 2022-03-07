package app.cash.chronicler.player.miskds

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.vertx.mysqlclient.MySQLConnectOptions


class DatasourceOptions : OptionGroup() {
  val host by option("--db-host")
  val database by option("--db-schema")
  val username by option("--db-user")
  val password by option("--db-password")
  val port by option("--db-port").int().default(3306)

  val isSet: Boolean by lazy {
    host != null
  }

  val mySQLConnectOptions: MySQLConnectOptions by lazy {
    MySQLConnectOptions().also { opt ->
      opt.host = host
      opt.database = database
      opt.user = username
      if (password != null) {
        opt.password = password
      }
      opt.port = port
    }
  }
}
