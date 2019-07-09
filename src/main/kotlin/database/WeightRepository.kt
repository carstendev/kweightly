package database


import model.SaveWeight
import model.Weight
import org.apache.logging.log4j.kotlin.Logging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
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

    fun upsert(weightToUpsert: Weight): Long {
        return transaction(db) {
            val userIds = WeightTable
                .select(WeightTable.id eq weightToUpsert.id)
                .map { it[WeightTable.userId] }

            if (userIds.isEmpty()) {

                WeightTable.insert {
                    it[id] = weightToUpsert.id
                    it[userId] = weightToUpsert.userId
                    it[weight] = weightToUpsert.weight
                    it[recordedAt] = DateTime(weightToUpsert.recordedAt.toEpochMilli(), DateTimeZone.UTC)
                    it[comment] = weightToUpsert.comment
                } get WeightTable.id

            } else {
                // make sure the userId is the same
                if(userIds.first() != weightToUpsert.userId)
                    throw UnauthorizedException("Users can only change their own resources!")

                WeightTable.update({ WeightTable.id eq weightToUpsert.id }) {
                    it[weight] = weightToUpsert.weight
                    it[recordedAt] = DateTime(weightToUpsert.recordedAt.toEpochMilli(), DateTimeZone.UTC)
                    it[comment] = weightToUpsert.comment
                }
                weightToUpsert.id
            }
        }
    }

    fun delete(id: Long, userId: String): Int {
        return transaction(db) {
            WeightTable.deleteWhere {
                WeightTable.id eq id and (WeightTable.userId eq userId)
            }
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

class UnauthorizedException(msg: String) : Exception(msg)