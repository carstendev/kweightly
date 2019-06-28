package service

import config.Config
import org.http4k.cloudnative.health.Health
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind

/**
 * Builds the health service routing handler.
 */
object HealthService {

    operator fun invoke(cfg: Config): HttpHandler {
        return Health(
            "/config" bind Method.GET to { Response(Status.OK).body(cfg.toString()) },
            checks = listOf()
        )
    }

}