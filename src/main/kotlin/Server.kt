import config.Config
import config.AppLoader
import database.WeightRepository
import org.apache.logging.log4j.kotlin.Logging
import org.http4k.cloudnative.Http4kK8sServer
import org.http4k.server.SunHttp
import org.jetbrains.exposed.sql.Database
import service.*
import java.util.*

object App : Logging {

    operator fun invoke(cfg: Config, db: Database): Http4kK8sServer {
        logger.info(cfg.toString())
        logger.info("Available processors: ${Runtime.getRuntime().availableProcessors()}")
        logger.info("Max available memory: ${Runtime.getRuntime().maxMemory()} bytes")

        val authFilter = AuthFilter(AppLoader.tokenAuthService(cfg.authConfig))
        val weightHandler = WeightService(WeightRepository(db), authFilter)
        val weightApp = SunHttp(cfg.serverConfig.servicePort).toServer(weightHandler)
        val healthApp = SunHttp(cfg.serverConfig.healthPort).toServer(HealthService(cfg, db))

        return Http4kK8sServer(weightApp, healthApp)
    }

}

fun main() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC")) // force timezone to utc!

    val cfg = AppLoader("application.conf")
    val db = AppLoader.migrateDatabase(cfg.dbConfig)
    App(cfg, db).start().block()
}