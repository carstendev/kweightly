import config.Config
import config.AppLoader
import config.DatabaseConfig
import database.WeightRepository
import org.http4k.cloudnative.Http4kK8sServer
import org.http4k.server.SunHttp
import org.jetbrains.exposed.sql.Database
import service.HealthService
import service.WeightService

object App {

    operator fun invoke(cfg: Config, db: Database): Http4kK8sServer {
        // define the main app API - it proxies to the "other" service
        val weightHandler = WeightService(WeightRepository(db))
        val weightApp = SunHttp(cfg.serverConfig.servicePort).toServer(weightHandler)
        val healthApp = SunHttp(cfg.serverConfig.healthPort).toServer(HealthService(cfg))

        return Http4kK8sServer(weightApp, healthApp)
    }

}

fun main() {
    val cfg = AppLoader("app.conf")
    val db = AppLoader.createDatabase(cfg.dbConfig)
    App(cfg, db).start()
}