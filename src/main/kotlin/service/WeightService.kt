package service

import database.UnauthorizedException
import database.WeightRepository
import model.SaveWeight
import model.Weight
import org.http4k.core.*
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson.auto
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.jose4j.jwt.JwtClaims
import java.util.*


class WeightService private constructor(weightRepo: WeightRepository, authFilter: Filter) {

    companion object {
        operator fun invoke(weightRepo: WeightRepository, authFilter: Filter): HttpHandler {
            return WeightService(weightRepo, authFilter).routes
        }
    }

    private val weightLens = Body.auto<List<Weight>>().toLens()
    private val saveWeightLens = Body.auto<SaveWeight>().toLens()
    private val putWeightLens = Body.auto<Weight>().toLens()
    private val composedFilter = ServerFilters.GZip().then(authFilter)

    private inline fun handleWithClaims(
        requiredPermissions: Set<Permission>,
        request: HttpMessage,
        handler: (claims: JwtClaims) -> Response
    ): Response {
        val claims = JwtClaims.parse(request.header("x-jwt-claims"))
        return claims?.let {
            val permissions = Permission.extractPermissions(it)
            if (permissions.containsAll(requiredPermissions)) {
                handler(it).header("Content-Type", ContentType.APPLICATION_JSON.value)
            } else {
                Response(Status.UNAUTHORIZED)
            }
        } ?: Response(Status.UNAUTHORIZED)
    }

    private val getHandler: HttpHandler = composedFilter.then { request ->
        handleWithClaims(setOf(Permission.ReadWeights), request) { claims ->
            weightLens(
                weightRepo.findAllByUserId(claims.subject),
                Response(Status.OK)
            )
        }
    }

    private val deleteHandler: HttpHandler = composedFilter.then { request ->
        handleWithClaims(setOf(Permission.DeleteWeights), request) { claims ->
            Optional.ofNullable(request.path("id"))
                .flatMap { Optional.ofNullable(it.toLongOrNull()) }
                .map {
                    weightRepo.delete(it, claims.subject)
                    Response(Status.NO_CONTENT)
                }.orElse(Response(Status.BAD_REQUEST))
        }
    }

    private val postHandler: HttpHandler = composedFilter.then { request ->
        handleWithClaims(setOf(Permission.AddWeights), request) { claims ->
            val newWeight = saveWeightLens(request)
            val newId = weightRepo.insert(newWeight, claims.subject)
            Response(Status.CREATED).body(newId.toString())
        }
    }

    private val putHandler: HttpHandler = composedFilter.then { request ->
        handleWithClaims(setOf(Permission.AddWeights), request) { claims ->
            val weight = putWeightLens(request)
            if (weight.userId == claims.subject) {
                try {
                    weightRepo.upsert(weight)
                    Response(Status.NO_CONTENT)
                } catch (e: UnauthorizedException) {
                    Response(Status.UNAUTHORIZED)
                }
            } else {
                Response(Status.UNAUTHORIZED)
            }
        }
    }

    private val routes = routes(
        "/api/weights" bind Method.GET to getHandler,
        "/api/weights/{id}" bind Method.DELETE to deleteHandler,
        "/api/weights" bind Method.POST to postHandler,
        "/api/weights" bind Method.PUT to putHandler
    )
}