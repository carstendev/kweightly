package service

import database.WeightRepository
import model.SaveWeight
import model.Weight
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.routing.bind
import org.http4k.routing.routes


object WeightService {

    val weightLens = Body.auto<List<Weight>>().toLens()
    val saveWeightLens = Body.auto<SaveWeight>().toLens()

    operator fun invoke(weightRepo: WeightRepository, authFilter: AuthFilter): HttpHandler {

        val getHandler: HttpHandler = authFilter.then { request ->
            val userId = request.header("x-user-id")
            userId?.let { userId ->
                weightLens(
                    weightRepo.findAllByUserId(userId),
                    Response(Status.OK).header("Content-Type", ContentType.APPLICATION_JSON.value)
                )
            } ?: Response(Status.UNAUTHORIZED)
        }

        val postHandler: HttpHandler = authFilter.then { request ->
            val userId = request.header("x-user-id")
            userId?.let { userId ->
                val newWeight = saveWeightLens(request)
                val newId = weightRepo.insert(newWeight, userId)
                Response(Status.OK).body(newId.toString())
            } ?: Response(Status.UNAUTHORIZED)
        }

        return routes(
            "/api/user/{userId}/weights" bind Method.GET to getHandler,
            "/api/user/{userId}/weights" bind Method.POST to postHandler
        )

    }

}