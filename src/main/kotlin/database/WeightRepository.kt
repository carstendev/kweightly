package database


import model.SaveWeight
import model.Weight
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object WeightTable : Table() {
    val id = long("id").primaryKey().autoIncrement()
    val userId = varchar("user_id", 50)
    val recordedAt = datetime("recorded_at")
    val weight = decimal("weight", 5, 2)
    val comment = varchar("comment", length = 255).nullable()
}

object SqlLog : SqlLogger {
    private val logger: Logger = LoggerFactory.getLogger("sql")

    override fun log (context: StatementContext, transaction: Transaction) {
        logger.debug("SQL: ${context.expandArgs(transaction)}")
    }
}

class WeightRepository(private val db: Database) {

    private val timeZone: DateTimeZone = DateTimeZone.forID("Europe/Berlin")
    private val zoneId = ZoneId.of("Europe/Berlin")

    fun insert(newWeight: SaveWeight, userId: String): Long {
        return transaction(db) {
            addLogger(SqlLog)
            WeightTable.insert {
                it[WeightTable.userId] = userId
                it[recordedAt] = DateTime.now(timeZone) // save with german tz info
                it[weight] = newWeight.weight
                it[comment] = newWeight.comment
            } get WeightTable.id
        }
    }

    fun findAllByUserId(userId: String): List<Weight> {
        return transaction(db) {
            addLogger(SqlLog)
            WeightTable
                .select(WeightTable.userId eq userId)
                .map { row ->
                    Weight(
                        id = row[WeightTable.id],
                        userId = row[WeightTable.userId],
                        recordedAt = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(row[WeightTable.recordedAt].millis),
                            zoneId
                        ),
                        weight = row[WeightTable.weight],
                        comment = row[WeightTable.comment]
                    )
                }
        }
    }

}
