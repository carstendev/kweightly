package service

import org.jose4j.jwt.JwtClaims

sealed class Permission {
    object ReadWeights : Permission()
    object AddWeights : Permission()
    object DeleteWeights : Permission()

    companion object {
        private fun fromString(string: String): Permission? {
            return when (string) {
                "add:weights" -> AddWeights
                "read:weights" -> ReadWeights
                "delete:weights" -> DeleteWeights
                else -> null
            }
        }

        fun extractPermissions(claims: JwtClaims): Set<Permission> {
            return claims
                .getStringListClaimValue("permissions")
                .flatMap { listOfNotNull(fromString(it)) }
                .toHashSet()
        }
    }
}


