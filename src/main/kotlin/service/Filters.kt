package service

import config.AuthConfig
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwk.HttpsJwks
import org.jose4j.jwt.consumer.InvalidJwtException
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.JwtContext
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver
import org.slf4j.LoggerFactory

class AuthFilter(private val authService: TokenAuthService) : Filter {
    override fun invoke(next: HttpHandler): HttpHandler = {
        it.header("Authorization")?.let { token ->
            authService.authorize(token)?.let { decodedToken ->
                next(it.header("x-jwt-claims", decodedToken.jwtClaims.toJson()))
            }
        } ?: Response(Status.UNAUTHORIZED)
    }
}

class TokenAuthService(private val jwt: JWT) {
    fun authorize(token: String): JwtContext? {
        return jwt.verify(token) ?: return null
    }
}


class JWT(private val cfg: AuthConfig) {
    private val log = LoggerFactory.getLogger("application")

    fun verify(token: String): JwtContext? {
        try {
            return verifier.process(token.split(" ").last()) // remove Bearer prefix
        } catch (e: InvalidJwtException) {
            log.info("Unable to verify JWT token", e)
            return null
        }
    }

    private val verifier by lazy {
        val httpsJwks = HttpsJwks(cfg.jwksLocation)
        val httpsJwksKeyResolver = HttpsJwksVerificationKeyResolver(httpsJwks)

        JwtConsumerBuilder()
            .setRequireExpirationTime()
            .setAllowedClockSkewInSeconds(30)
            .setRequireSubject()
            .setJweAlgorithmConstraints(AlgorithmConstraints.DISALLOW_NONE)
            .setExpectedIssuer(cfg.issuer)
            .setVerificationKeyResolver(httpsJwksKeyResolver)
            .setExpectedAudience(cfg.audience)
            .build()
    }
}