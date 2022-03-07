package app.cash.chronicler.player.miskds

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import io.vertx.mysqlclient.MySQLConnectOptions
import java.io.File

class TargetMiskDatasourceOptions : OptionGroup() {
  val dataSourceYaml by option("--misk-datasource-yaml", help = "Location of yaml file containing datasource configuration in misk-datasource format.")
  val cluster by option("--misk-datasource-cluster", help = "Name of the cluster to use from the 'misk-datasource-yaml' file.")
  val type by option("--misk-datasource-cluster-type", help = "Cluster type to use from the 'misk-datasource-yaml' file.")
    .choice("reader", "writer")
    .default("writer")

  val mySQLConnectOptions: MySQLConnectOptions by lazy {
    check(dataSourceYaml != null) { "--misk-datasource-yaml or --db-host must be set" }
    check(type == "writer") { "Reader type is not supported" }
    MiskDatasourceConfigFile(File(dataSourceYaml!!))
      .parseConfig(cluster!!, type)
      .toMySQLConnectOptions()
  }
}
