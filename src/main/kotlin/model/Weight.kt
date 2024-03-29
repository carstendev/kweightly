package model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

data class SaveWeight(
    val weight: BigDecimal,
    val comment: String?
)

data class Weight(
    val id: Long,
    val userId: String,
    val recordedAt: Instant,
    val weight: BigDecimal,
    val comment: String?
)
