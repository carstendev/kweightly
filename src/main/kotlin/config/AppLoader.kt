package config

import com.typesafe.config.ConfigFactory
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.source.hocon.HoconSource
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.http4k.cloudnative.env.Secret
import org.jetbrains.exposed.sql.Database
import service.JWT
import service.TokenAuthService
import service.TokenAuthServiceImpl
import java.util.*

data class DatabaseConfig(val driver: String, val url: String, val user: String, val password: Optional<Secret>)
data class ServerConfig(val host: String, val servicePort: Int, val healthPort: Int)
data class AuthConfig(val jwksLocation: String, val issuer: String, val audience: String)
data class Config(val serverConfig: ServerConfig, val dbConfig: DatabaseConfig, val authConfig: AuthConfig)

object ServerSpec : ConfigSpec("server") {
    val host by optional("0.0.0.0")
    val servicePort by required<Int>()
    val healthPort by required<Int>()
}

object DatabaseSpec : ConfigSpec("database") {
    val driver by required<String>()
    val url by required<String>()
    val user by required<String>()
    val password by required<String>()
}

object AuthSpec : ConfigSpec("auth") {
    val jwksLocation by required<String>()
    val issuer by required<String>()
    val audience by required<String>()
}

/**
 * Responsible for loading the config during app start in a typesafe manner.
 * Config loading is fail-fast, and the app will not start, if the config is malformed or required parameters are missing!
 */
object AppLoader {

    fun tokenAuthService(cfg: AuthConfig): TokenAuthService {
        return TokenAuthServiceImpl(
            JWT(cfg)
        )
    }

    fun migrateDatabase(cfg: DatabaseConfig): Database {
        val password =
            cfg.password.map { it.use { pw -> pw } }.orElse("")

        Flyway.configure().dataSource(
            cfg.url,
            cfg.user,
            password
        ).load().migrate()

        val hikariCfg = HikariConfig()
        hikariCfg.driverClassName = cfg.driver
        hikariCfg.jdbcUrl = cfg.url
        hikariCfg.username = cfg.user
        hikariCfg.password = password
        hikariCfg.maximumPoolSize = 3 // connections = ((core_count * 2) + effective_spindle_count)
        return Database.connect(HikariDataSource(hikariCfg))
    }

    private fun buildConfig(configPath: String): com.uchuhimo.konf.Config {
        val config = com.uchuhimo.konf.Config()
        config.addSpec(ServerSpec)
        config.addSpec(DatabaseSpec)
        config.addSpec(AuthSpec)

        val source =
            HoconSource(ConfigFactory.parseResources(configPath).resolve().root()) //TODO: report bug back to konf!
        return config.from.hocon.config.withSource(source)
    }

    operator fun invoke(configPath: String): Config {
        val configSet = buildConfig(configPath)
        val pw = configSet[DatabaseSpec.password]
        return Config(
            ServerConfig(
                host = configSet[ServerSpec.host],
                servicePort = configSet[ServerSpec.servicePort],
                healthPort = configSet[ServerSpec.healthPort]
            ),
            DatabaseConfig(
                driver = configSet[DatabaseSpec.driver],
                url = configSet[DatabaseSpec.url],
                user = configSet[DatabaseSpec.user],
                password = if (pw.isEmpty()) Optional.empty() else Optional.of(Secret(pw))
            ),
            AuthConfig(
                jwksLocation = configSet[AuthSpec.jwksLocation],
                issuer = configSet[AuthSpec.issuer],
                audience = configSet[AuthSpec.audience]
            )
        )
    }
}