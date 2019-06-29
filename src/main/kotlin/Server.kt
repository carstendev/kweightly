import config.Config
import config.AppLoader
import database.WeightRepository
import org.http4k.cloudnative.Http4kK8sServer
import org.http4k.server.SunHttp
import org.jetbrains.exposed.sql.Database
import service.*

object App {

    operator fun invoke(cfg: Config, db: Database): Http4kK8sServer {

        val authFilter = AuthFilter(AppLoader.tokenAuthService(cfg.authConfig))
        val weightHandler = WeightService(WeightRepository(db), authFilter)
        val weightApp = SunHttp(cfg.serverConfig.servicePort).toServer(weightHandler)
        val healthApp = SunHttp(cfg.serverConfig.healthPort).toServer(HealthService(cfg, db))

        return Http4kK8sServer(weightApp, healthApp)
    }

}

fun main() {
    val cfg = AppLoader("app.conf")
    val db = AppLoader.createDatabase(cfg.dbConfig)
    App(cfg, db).start()
}