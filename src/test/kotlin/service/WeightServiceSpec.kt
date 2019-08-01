package service

import config.AppLoader
import database.WeightRepository
import io.kotlintest.*
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.specs.StringSpec
import org.http4k.core.*
import org.jetbrains.exposed.sql.Database
import org.jose4j.jwt.JwtClaims

class WeightServiceSpec : StringSpec() {

    override fun isolationMode(): IsolationMode? =
        IsolationMode.InstancePerTest

    override fun afterTest(testCase: TestCase, result: TestResult) {
        db.connector().prepareStatement("DROP table weight;").execute()
        db.connector().prepareStatement("DROP table flyway_schema_history;").execute()
    }

    val cfg = AppLoader("application.test.conf")
    val db = Database.connect(AppLoader.migrateDatabase(cfg.dbConfig))
    val weightService = WeightService(WeightRepository(db), Filter.NoOp) // disabled authN/authZ check with auth0.com

    init {
        "/api/weights should correctly handle GET, POST, PUT and DELETE requests" {
            val claims = JwtClaims()
            claims.subject = "test-subject"
            claims.setStringListClaim("permissions", "add:weights", "read:weights", "delete:weights")
            val requestClaimsHeader: Headers = listOf(Pair("x-jwt-claims", claims.toJson()))

            val initialResponse = weightService(Request(Method.GET, "/api/weights").headers(requestClaimsHeader))
            initialResponse.status shouldBe Status.OK
            initialResponse.bodyString() shouldBe "[]" // --> no weights added yet

            val postRequest = Request(Method.POST, "/api/weights")
                .body("""{"weight": 80.1,"comment": "Looking good!"}""")
                .headers(requestClaimsHeader)

            val postResponse = weightService(postRequest)
            postResponse.status shouldBe Status.CREATED // --> one weight created
            postResponse.bodyString() shouldBe "1" // id of the created resource should be returned

            val responseAfterPost = weightService(Request(Method.GET, "/api/weights").headers(requestClaimsHeader))
            responseAfterPost.status shouldBe Status.OK // --> all the weights for the requesting user
            responseAfterPost.bodyString() shouldContain """"id":1"""
            responseAfterPost.bodyString() shouldContain """"userId":"test-subject""""
            responseAfterPost.bodyString() shouldContain """"weight":80.1"""
            responseAfterPost.bodyString() shouldContain """"comment":"Looking good!""""

            val putRequest = Request(Method.PUT, "/api/weights")
                .body("""{"id":1,"userId":"test-subject","recordedAt":"2019-07-31T14:52:44.164Z","weight":80.1,"comment":"Looking super good!"}""")
                .headers(requestClaimsHeader)

            val responseAfterPut = weightService(putRequest)
            responseAfterPut.status shouldBe Status.NO_CONTENT

            val responseAfterUpdate = weightService(Request(Method.GET, "/api/weights").headers(requestClaimsHeader))
            responseAfterUpdate.bodyString() shouldContain """"id":1"""
            responseAfterUpdate.bodyString() shouldContain """"comment":"Looking super good!""""

            val responseAfterDelete =
                weightService(Request(Method.DELETE, "/api/weights/1").headers(requestClaimsHeader))
            responseAfterDelete.status shouldBe Status.NO_CONTENT

            val lastResponse = weightService(Request(Method.GET, "/api/weights").headers(requestClaimsHeader))
            lastResponse.status shouldBe Status.OK
            lastResponse.bodyString() shouldBe "[]" // --> all weights removed
        }

        "/api/weights should respect authZ" {
            val claims = JwtClaims()
            claims.subject = "test-subject3"
            val requestClaimsHeader: Headers = listOf(Pair("x-jwt-claims", claims.toJson()))

            val get = weightService(Request(Method.GET, "/api/weights").headers(requestClaimsHeader))
            get.status shouldBe Status.UNAUTHORIZED
            get.bodyString() shouldBe ""

            val post = Request(Method.POST, "/api/weights")
                .body("""{"weight": 80.1,"comment": "Looking good!"}""")
                .headers(requestClaimsHeader)

            weightService(post).status shouldBe Status.UNAUTHORIZED

            val put = Request(Method.PUT, "/api/weights")
                .body("""{"id":1,"userId":"test-subject","recordedAt":"2019-07-31T14:52:44.164Z","weight":80.1,"comment":"Looking super good!"}""")
                .headers(requestClaimsHeader)

            weightService(put).status shouldBe Status.UNAUTHORIZED

            val delete = weightService(Request(Method.DELETE, "/api/weights/1").headers(requestClaimsHeader))
            delete.status shouldBe Status.UNAUTHORIZED
        }

        "/api/weights should respect authN and not leak user information" {
            val userOneClaims = JwtClaims()
            userOneClaims.subject = "test-subject1"
            userOneClaims.setStringListClaim("permissions", "add:weights", "read:weights", "delete:weights")
            val requestClaimsHeaderOne: Headers = listOf(Pair("x-jwt-claims", userOneClaims.toJson()))

            val userTwoClaims = JwtClaims()
            userTwoClaims.subject = "test-subject2"
            userTwoClaims.setStringListClaim("permissions", "add:weights", "read:weights", "delete:weights")
            val requestClaimsHeaderTwo: Headers = listOf(Pair("x-jwt-claims", userTwoClaims.toJson()))

            weightService(
                Request(Method.POST, "/api/weights")
                    .body("""{"weight": 70.1,"comment": "Looking good!"}""")
                    .headers(requestClaimsHeaderOne)
            ).status shouldBe Status.CREATED

            val userOneGet = weightService(Request(Method.GET, "/api/weights").headers(requestClaimsHeaderOne))
            userOneGet.status shouldBe Status.OK
            userOneGet.bodyString() shouldContain """test-subject1"""

            val userTwoGet = weightService(Request(Method.GET, "/api/weights").headers(requestClaimsHeaderTwo))
            userTwoGet.status shouldBe Status.OK
            userTwoGet.bodyString() shouldBe "[]"

            val userTwoPutForUserOne = Request(Method.PUT, "/api/weights")
                .body("""{"id":15,"userId":"test-subject1","recordedAt":"2019-07-31T14:52:44.164Z","weight":80.1,"comment":"Looking super good!"}""")
                .headers(requestClaimsHeaderTwo)

            weightService(userTwoPutForUserOne).status shouldBe Status.UNAUTHORIZED

            weightService(
                Request(Method.DELETE, "/api/weights/2").headers(requestClaimsHeaderTwo)
            ).status shouldBe Status.NO_CONTENT // returns no content but must not delete

            val verifyResourceNotDeleted =
                weightService(Request(Method.GET, "/api/weights").headers(requestClaimsHeaderOne))
            verifyResourceNotDeleted.status shouldBe Status.OK
            verifyResourceNotDeleted.bodyString() shouldContain """test-subject1"""
        }
    }
}