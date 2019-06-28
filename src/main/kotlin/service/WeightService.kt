package service

import com.google.gson.Gson
import database.WeightRepository
import model.SaveWeight
import org.http4k.core.*
import org.http4k.lens.string
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes


object WeightService {

    val gson = Gson()
    val responseBody = Body.string(ContentType.APPLICATION_JSON).toLens()

    operator fun invoke(weightRepo: WeightRepository): HttpHandler {
        return routes(
            "/api/user/{userId}/weights" bind Method.GET to { req: Request ->
                val userId = requireNotNull(req.path("userId"))
                val weightsForUser = weightRepo.findAllByUserId(userId)
                Response(Status.OK)
                    .header("Content-Type", ContentType.APPLICATION_JSON.value)
                    .body(gson.toJson(weightsForUser))
            },
            "/api/user/{userId}/weights" bind Method.POST to { req: Request ->
                val userId = requireNotNull(req.path("userId"))
                val newWeight = gson.fromJson<SaveWeight>(responseBody(req), SaveWeight::class.java)
                val newId = weightRepo.insert(newWeight, userId)
                Response(Status.OK).body(gson.toJson(newId))
            }
        )

    }

}