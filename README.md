# Chronicler
MySQL query recorder/replayer library.

Provides a simple mechanism to record full query log for a sampled set of service requests. Records contextual information such as request id, thread id, timestamps, parameters, etc. This allows to replay production queries for purposes of comparing performance of different database configurations or implementations.

## recorder-core
A library intended to be used with the MySQL Connector/J driver. Provides a `QueryInterceptor` implementation - `ChroniclerInterceptor`, which can be registered with the driver to sample the MySQL requests.
Interceptor can be configured with different sampling strategies and sinks. 

### Usage
1. Create an implementation of `ChroniclerInterceptor.Config`, and instantiate it. This configuration is used to decide which queries need to be sampled, and where to deposit the results (sink). 
2. In your application code, before any MySQL connections have been established, use `ChroniclerConfigRepository::set(name, config)` to register your named chronicler configuration. `name` can be anything, for instance `"main"`.
3. Modify the MySQL JDBC connection string to include a query interceptor and `chroniclerConfigName` (any identifier). For example:
```
jdbc:mysql://localhost?queryInterceptors=com.squareup.cash.chronicler.ChroniclerInterceptor&chroniclerConfigName=main
```

At this point your sink configuration inside `ChroniclerInterceptor.Config` should start receiving requests according to your provided sampling rules.

## recorder-kpl
A Kinesis-based implementation of recorder-core. Simplifies setup when the underlying sink is Kinesis Data Streams. 

### Usage
1. Instantiate `CanonicalChroniclerInterceptorConfig` providing it all required information.
2. Register the configuration instance with `ChroniclerConfigRepository::set(name, config)` and modify the JDBC URL accordingly (see recorder-core->Usage).
3. Call `init` method to provide dynamic (change-able without restart) parts of the configuration. This method can be called repeatedly, which allows for wiring it with a feature flag implementation.

## Player
Chronicler-player is a stand-alone application designed to:
* Read chronicler recorded messages from Kinesis; 
* Re-aggregate them into the shape of original application-level requests; 
* Replay them on a provided MySQL-compatible database retaining original intra-query delays;
* Record and output replay metrics.

### Usage
Chronicler-player can be executed as a standalone java application: `java -jar chronicler-release.jar <arguments>`. To see the possible arguments run `java -jar chronicler-release.jar --help`.