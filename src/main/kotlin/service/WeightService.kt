package service

import database.UnauthorizedException
import database.WeightRepository
import model.SaveWeight
import model.Weight
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.jose4j.jwt.JwtClaims
import java.util.*


object WeightService {

    val weightLens = Body.auto<List<Weight>>().toLens()
    val saveWeightLens = Body.auto<SaveWeight>().toLens()
    val putWeightLens = Body.auto<Weight>().toLens()

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

    operator fun invoke(weightRepo: WeightRepository, authFilter: AuthFilter): HttpHandler {

        val getHandler: HttpHandler = authFilter.then { request ->
            handleWithClaims(setOf(Permission.ReadWeights), request) { claims ->
                weightLens(
                    weightRepo.findAllByUserId(claims.subject),
                    Response(Status.OK)
                )
            }
        }

        val deleteHandler: HttpHandler = authFilter.then { request ->
            handleWithClaims(setOf(Permission.DeleteWeights), request) { claims ->
                Optional.ofNullable(request.path("id"))
                    .flatMap { Optional.ofNullable(it.toLongOrNull()) }
                    .map {
                        weightRepo.delete(it, claims.subject)
                        Response(Status.NO_CONTENT)
                    }.orElse(Response(Status.BAD_REQUEST))
            }
        }

        val postHandler: HttpHandler = authFilter.then { request ->
            handleWithClaims(setOf(Permission.AddWeights), request) { claims ->
                val newWeight = saveWeightLens(request)
                val newId = weightRepo.insert(newWeight, claims.subject)
                Response(Status.OK).body(newId.toString())
            }
        }

        val putHandler: HttpHandler = authFilter.then { request ->
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

        return routes(
            "/api/weights" bind Method.GET to getHandler,
            "/api/weights/{id}" bind Method.DELETE to deleteHandler,
            "/api/weights" bind Method.POST to postHandler,
            "/api/weights" bind Method.PUT to putHandler
        )

    }

}