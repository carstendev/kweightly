package service

import database.WeightRepository
import model.SaveWeight
import model.Weight
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.jose4j.jwt.JwtClaims


object WeightService {

    val weightLens = Body.auto<List<Weight>>().toLens()
    val saveWeightLens = Body.auto<SaveWeight>().toLens()

    private inline fun handleWithClaims(request: HttpMessage, handler: (claims: JwtClaims) -> Response): Response {
        val claims = JwtClaims.parse(request.header("x-jwt-claims"))
        return claims?.let {
            handler(it)
        } ?: Response(Status.UNAUTHORIZED)
    }

    operator fun invoke(weightRepo: WeightRepository, authFilter: AuthFilter): HttpHandler {

        val getHandler: HttpHandler = authFilter.then { request ->
            handleWithClaims(request) { claims ->
                weightLens(
                    weightRepo.findAllByUserId(claims.subject),
                    Response(Status.OK).header("Content-Type", ContentType.APPLICATION_JSON.value)
                )
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
            "/api/user/weights" bind Method.GET to getHandler,
            "/api/user/weights" bind Method.POST to postHandler
        )

    }

}