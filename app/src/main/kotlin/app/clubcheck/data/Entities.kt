package app.clubcheck.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "check_ins", indices = [Index(value = ["eventId"], unique = true)])
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val payloadHex: String,
    val imagePath: String,
    val state: String = "QUEUED",
    val createdAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "clubs")
data class ClubEntity(@PrimaryKey val url: String, val title: String?, val summary: String?)

@Dao
interface CheckInDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: CheckInEntity): Long
    @Query("SELECT * FROM check_ins WHERE state = 'QUEUED' ORDER BY createdAtMs") suspend fun queued(): List<CheckInEntity>
    @Query("UPDATE check_ins SET state = 'SENT' WHERE id = :id") suspend fun markSent(id: Long)
    @Query("SELECT * FROM check_ins ORDER BY createdAtMs DESC") suspend fun all(): List<CheckInEntity>
}

@Dao
interface ClubDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(club: ClubEntity)
    @Query("SELECT * FROM clubs WHERE url = :url") suspend fun find(url: String): ClubEntity?
}

@Database(entities = [CheckInEntity::class, ClubEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun checkIns(): CheckInDao
    abstract fun clubs(): ClubDao
}
