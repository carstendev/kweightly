package service

import config.AuthConfig
import org.apache.logging.log4j.kotlin.logger
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

class AuthFilter(private val authService: TokenAuthService) : Filter {
    override fun invoke(next: HttpHandler): HttpHandler = {
        it.header("Authorization")?.let { token ->
            authService.authorize(token)?.let { decodedToken ->
                next(it.header("x-jwt-claims", decodedToken.jwtClaims.toJson()))
            } ?: Response(Status.UNAUTHORIZED)
        } ?: Response(Status.UNAUTHORIZED)
    }
}

interface TokenAuthService {
    fun authorize(token: String): JwtContext?
}

class TokenAuthServiceImpl(private val jwt: JWT) : TokenAuthService {
    override fun authorize(token: String): JwtContext? {
        return jwt.verify(token)
    }
}

class JWT(private val cfg: AuthConfig) {
    private val log = logger()

    fun verify(token: String): JwtContext? {
        return try {
            verifier.process(token.split(" ").last()) // remove Bearer prefix
        } catch (e: InvalidJwtException) {
            log.info("Unable to verify JWT token", e)
            null
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