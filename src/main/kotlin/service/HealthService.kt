package service

import config.Config
import org.http4k.cloudnative.health.*
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Builds the health service routing handler.
 */
object HealthService {

    operator fun invoke(cfg: Config, db: Database, metricService: MetricService): HttpHandler {
        return Health(
            "/config" bind Method.GET to { Response(Status.OK).body(cfg.toString()) },
            "/metrics" bind Method.GET to metricService.getHandler,
            checks = listOf(
                DatabaseCheck(db)
            )
        )
    }

}

class DatabaseCheck(private val db: Database) : ReadinessCheck {
    override val name = "database"
    override fun invoke(): ReadinessCheckResult {
        return transaction(db) {
            exec("select 1;") { Completed(name) }
        } ?: Failed(name, DatabaseCheckException("Could not reach db!"))
    }
}

class DatabaseCheckException(msg: String) : Exception(msg)