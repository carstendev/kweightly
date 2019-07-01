package database


import model.SaveWeight
import model.Weight
import org.apache.logging.log4j.kotlin.Logging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object WeightTable : Table() {
    val id = long("id").primaryKey().autoIncrement()
    val userId = varchar("user_id", 50)
    val recordedAt = datetime("recorded_at")
    val weight = decimal("weight", 5, 2)
    val comment = varchar("comment", length = 255).nullable()
}

class WeightRepository(private val db: Database) : Logging {

    fun insert(newWeight: SaveWeight, userId: String): Long {
        return transaction(db) {
            WeightTable.insert {
                it[WeightTable.userId] = userId
                it[weight] = newWeight.weight
                it[comment] = newWeight.comment
            } get WeightTable.id
        }
    }

    fun findAllByUserId(userId: String): List<Weight> {
        return transaction(db) {
            WeightTable
                .select(WeightTable.userId eq userId)
                .map { row ->
                    Weight(
                        id = row[WeightTable.id],
                        userId = row[WeightTable.userId],
                        recordedAt = Instant.ofEpochMilli(row[WeightTable.recordedAt].millis),
                        weight = row[WeightTable.weight],
                        comment = row[WeightTable.comment]
                    )
                }
        }
    }

}
