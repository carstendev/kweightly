package service

import io.micrometer.prometheus.PrometheusMeterRegistry
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status

class MetricService(registry: PrometheusMeterRegistry) {
    val getHandler: HttpHandler = { Response(Status.OK).body(registry.scrape()) }
}
