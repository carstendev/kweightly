package service

import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import org.http4k.cloudnative.env.Secret
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory

class AuthFilter(private val authService: TokenAuthService) : Filter {
    override fun invoke(next: HttpHandler): HttpHandler = {
        it.header("Authorization")?.let { token ->
            authService.authorize(token)?.let { decodedToken ->
                next(it.header("x-user-id", decodedToken.subject.toString()))
            }
        } ?: Response(Status.UNAUTHORIZED)
    }
}

class TokenAuthService(private val jwt: JWT) {
    fun authorize(token: String): DecodedJWT? {
        return jwt.verify(token) ?: return null
    }
}


class JWT(private val secret: Secret, val issuer: String) {
    private val log = LoggerFactory.getLogger("application")

    fun verify(token: String): DecodedJWT? {
        try {
            return verifier.verify(token)
        } catch (e: JWTVerificationException) {
            log.info("Unable to verify JWT token", e)
            return null
        }
    }

    private val verifier by lazy {
        val algorithm = Algorithm.HMAC256(secret.use { x -> x })
        com.auth0.jwt.JWT
            .require(algorithm)
            .withIssuer(issuer)
            .build()
    }
}