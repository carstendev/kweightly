import config.AppLoader
import config.Config
import database.WeightRepository
import io.micrometer.core.instrument.Metrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.apache.logging.log4j.kotlin.Logging
import org.apache.logging.log4j.kotlin.logger
import org.http4k.cloudnative.Http4kK8sServer
import org.http4k.core.Method
import org.http4k.core.then
import org.http4k.filter.MetricFilters
import org.http4k.filter.ResilienceFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.jetbrains.exposed.sql.Database
import service.AuthFilter
import service.HealthService
import service.MetricService
import service.WeightService
import java.util.*
import kotlin.concurrent.thread


object App : Logging {

    operator fun invoke(cfg: Config, db: Database): Http4kK8sServer {
        logger.info(cfg.toString())
        logger.info("Available processors: ${Runtime.getRuntime().availableProcessors()}")
        logger.info("Max available memory: ${Runtime.getRuntime().maxMemory()} bytes")

        val authFilter = AuthFilter(AppLoader.tokenAuthService(cfg.authConfig))
        val weightHandler = WeightService(WeightRepository(db), authFilter)

        val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        Metrics.addRegistry(prometheusRegistry) // register global registry

        val metricHandler = MetricService(prometheusRegistry)
        val rateLimiter = ResilienceFilters.RateLimit() // default rate limiter -> 50 request per 5 sec

        val appRoutes = routes(
            "/api/weights" bind Method.GET to rateLimiter(weightHandler.getHandler),
            "/api/weights/{id}" bind Method.DELETE to rateLimiter(weightHandler.deleteHandler),
            "/api/weights" bind Method.POST to rateLimiter(weightHandler.postHandler),
            "/api/weights" bind Method.PUT to rateLimiter(weightHandler.putHandler)
        )

        val app = MetricFilters.Server.RequestCounter(prometheusRegistry)
            .then(MetricFilters.Server.RequestTimer(prometheusRegistry))
            .then(appRoutes)

        val mainApp = Jetty(cfg.serverConfig.servicePort).toServer(app)
        val healthApp = Jetty(cfg.serverConfig.healthPort).toServer(HealthService(cfg, db, metricHandler))

        return Http4kK8sServer(mainApp, healthApp)
    }

}

fun main() {
    val logger = logger("Server")
    TimeZone.setDefault(TimeZone.getTimeZone("UTC")) // force timezone to utc!

    val cfg = AppLoader("application.conf")
    val dataSource = AppLoader.migrateDatabase(cfg.dbConfig)
    val app = App(cfg, Database.connect(dataSource)).start()

    Runtime.getRuntime().addShutdownHook(
        thread(start = false, isDaemon = false, name = "ShutdownHookThread") {
            logger.info("Shutdown hook executed...")
            dataSource.close()
            app.close()
            Metrics.globalRegistry.close()
            logger.info("Shutdown hook finished...")
        }
    )
}