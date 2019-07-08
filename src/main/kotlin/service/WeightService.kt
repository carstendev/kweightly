package service

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

    private inline fun handleWithClaims(request: HttpMessage, handler: (claims: JwtClaims) -> Response): Response {
        val claims = JwtClaims.parse(request.header("x-jwt-claims"))
        return claims?.let {
            handler(it).header("Content-Type", ContentType.APPLICATION_JSON.value)
        } ?: Response(Status.UNAUTHORIZED)
    }

    operator fun invoke(weightRepo: WeightRepository, authFilter: AuthFilter): HttpHandler {

        val getHandler: HttpHandler = authFilter.then { request ->
            handleWithClaims(request) { claims ->
                weightLens(
                    weightRepo.findAllByUserId(claims.subject),
                    Response(Status.OK)
                )
            }
        }

        val deleteHandler: HttpHandler = authFilter.then { request ->
            handleWithClaims(request) { claims ->
                Optional.ofNullable(request.path("id"))
                    .flatMap { Optional.ofNullable(it.toLongOrNull()) }
                    .map {
                        weightRepo.delete(it, claims.subject)
                        Response(Status.NO_CONTENT)
                    }.orElse(Response(Status.BAD_REQUEST))
            }
        }

        val postHandler: HttpHandler = authFilter.then { request ->
            handleWithClaims(request) { claims ->
                val newWeight = saveWeightLens(request)
                val newId = weightRepo.insert(newWeight, claims.subject)
                Response(Status.OK).body(newId.toString())
            }
        }

        return routes(
            "/api/weights" bind Method.GET to getHandler,
            "/api/weights/{id}" bind Method.DELETE to deleteHandler,
            "/api/weights" bind Method.POST to postHandler
        )

    }

}