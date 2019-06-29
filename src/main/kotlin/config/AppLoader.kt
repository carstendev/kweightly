package config

import com.uchuhimo.konf.ConfigSpec
import database.WeightTable
import org.http4k.cloudnative.env.Secret
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import service.JWT
import service.TokenAuthService
import java.util.*

data class DatabaseConfig(val driver: String, val url: String, val user: String, val password: Optional<Secret>)
data class ServerConfig(val host: String, val servicePort: Int, val healthPort: Int)
data class AuthConfig(val secret: Secret, val issuer: String)
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
    val secret by required<String>()
    val issuer by required<String>()
}

/**
 * Responsible for loading the config during app start in a typesafe manner.
 * Config loading is fail-fast, and the app will not start, if the config is malformed or required parameters are missing!
 */
object AppLoader {

    fun tokenAuthService(cfg: AuthConfig): TokenAuthService {
        return TokenAuthService(
            JWT(
                cfg.secret,
                cfg.issuer
            )
        )
    }

    fun createDatabase(cfg: DatabaseConfig): Database {
        val db = Database.connect( // TODO: use connection pool (hikari)
            url = cfg.url,
            driver = cfg.driver,
            user = cfg.user,
            password = cfg.password.map { secret -> secret.use { pw -> pw } }.orElse("")

        )
        transaction(db) {
            SchemaUtils.create(WeightTable)
        }
        return db
    }

    private fun buildConfig(configPath: String): com.uchuhimo.konf.Config {
        val config = com.uchuhimo.konf.Config()
        config.addSpec(ServerSpec)
        config.addSpec(DatabaseSpec)
        return config.from.hocon.resource(configPath)
    }

    operator fun invoke(configPath: String): Config {
        val configSet = buildConfig(configPath)
        val pw = configSet[DatabaseSpec.password]
        val appConfig = Config(
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
                secret = Secret(configSet[AuthSpec.secret]),
                issuer = configSet[AuthSpec.issuer]
            )
        )
        return appConfig;
    }
}