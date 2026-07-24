package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val title: String = "",
    val tone: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val traits: String,
    val physicalDescription: String
)

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY timestamp DESC")
    fun getAllStories(): Flow<List<StoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity): Long

    @Query("DELETE FROM stories WHERE id = :id")
    suspend fun deleteStoryById(id: Int)
}

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters")
    fun getAllCharacters(): Flow<List<CharacterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterEntity): Long

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteCharacterById(id: Int)
}

@Database(entities = [StoryEntity::class, CharacterEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun characterDao(): CharacterDao

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "story_database")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

class StoryRepository(private val storyDao: StoryDao) {
    val allStories: Flow<List<StoryEntity>> = storyDao.getAllStories()

    suspend fun insert(story: StoryEntity): Long = storyDao.insertStory(story)
    suspend fun deleteById(id: Int) = storyDao.deleteStoryById(id)
}

class CharacterRepository(private val characterDao: CharacterDao) {
    val allCharacters: Flow<List<CharacterEntity>> = characterDao.getAllCharacters()

    suspend fun insert(character: CharacterEntity): Long = characterDao.insertCharacter(character)
    suspend fun deleteById(id: Int) = characterDao.deleteCharacterById(id)
}
