package service

import config.AppLoader
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.jetbrains.exposed.sql.Database

class HealthServiceSpec : StringSpec({

    val cfg = AppLoader("application.test.conf")
    val db = Database.connect(
        url = cfg.dbConfig.url,
        driver = cfg.dbConfig.driver,
        user = cfg.dbConfig.user,
        password = cfg.dbConfig.password.map { it.use { pw -> pw } }.orElse("")
    )

    val unhealthyDb = Database.connect(
        url = "unhealthy",
        driver = cfg.dbConfig.driver,
        user = cfg.dbConfig.user,
        password = cfg.dbConfig.password.map { it.use { pw -> pw } }.orElse("")
    )

    val metricService = MetricService(PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
    val healthyHealthService = HealthService(cfg, db, metricService)
    val unhealthyHealthService = HealthService(cfg, unhealthyDb, metricService)

    "/config should return the loaded cfg" {
        val response = healthyHealthService(Request(Method.GET, "/config"))
        response.status shouldBe Status.OK
        response.bodyString() shouldBe cfg.toString()
    }

    "/metric should return metrics" {
        val response = healthyHealthService(Request(Method.GET, "/metrics"))
        response.status shouldBe Status.OK
    }

    "/liveness should return ok (liveness checks for responsiveness of the app)" {
        val response = healthyHealthService(Request(Method.GET, "/liveness"))
        response.status shouldBe Status.OK
    }

    "/readiness should return ok (readiness also checks dependencies)" {
        val response = healthyHealthService(Request(Method.GET, "/readiness"))
        response.status shouldBe Status.OK
        response.bodyString() shouldBe "database=true"
    }

    "/readiness should return service unavailable" {
        val response = unhealthyHealthService(Request(Method.GET, "/readiness"))
        response.status shouldBe Status.SERVICE_UNAVAILABLE
        response.bodyString() shouldContain "database=false"
    }

})