package service

import config.Config
import org.http4k.cloudnative.health.Completed
import org.http4k.cloudnative.health.Health
import org.http4k.cloudnative.health.ReadinessCheck
import org.http4k.cloudnative.health.ReadinessCheckResult
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

    operator fun invoke(cfg: Config, db: Database): HttpHandler {
        return Health(
            "/config" bind Method.GET to { Response(Status.OK).body(cfg.toString()) },
            checks = listOf(
                DatabaseCheck(db)
            )
        )
    }

}

class DatabaseCheck(private val db: Database) : ReadinessCheck {
    override val name = "database"
    override fun invoke(): ReadinessCheckResult {
        transaction(db) {
            exec("select 1;") { r -> //TODO: use arrow -> Try here
                r
            }
        }
        return Completed(name)
    }
}