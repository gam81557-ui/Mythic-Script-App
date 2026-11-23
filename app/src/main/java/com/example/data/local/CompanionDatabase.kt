package com.example.data.local

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

@Entity(tableName = "skin_scripts")
data class SkinScriptEntity(
    @PrimaryKey val scriptId: String,
    val heroId: String,
    val skinName: String,
    val tierTag: String,
    val version: String,
    val compatibilityNotes: String,
    val targetRelativePathsJoined: String,
    val stagedDirPath: String,
    val isUserImported: Boolean,
    val persistedStatus: String, // IDLE, INJECTED, ERROR
    val errorMessage: String? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "backup_entries",
    primaryKeys = ["scriptId", "relativePath"],
)
data class BackupEntryEntity(
    val scriptId: String,
    val heroId: String,
    val relativePath: String,
    val backupFilePath: String,
    val existedBeforeInjection: Boolean,
    val byteSize: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface CompanionDao {
    @Query("SELECT * FROM skin_scripts ORDER BY isUserImported DESC, lastUpdatedAt DESC")
    fun observeAllScripts(): Flow<List<SkinScriptEntity>>

    @Query("SELECT * FROM skin_scripts WHERE heroId = :heroId ORDER BY isUserImported DESC, lastUpdatedAt DESC")
    fun observeScriptsForHero(heroId: String): Flow<List<SkinScriptEntity>>

    @Query("SELECT * FROM skin_scripts WHERE scriptId = :scriptId LIMIT 1")
    suspend fun getScriptById(scriptId: String): SkinScriptEntity?

    @Query("SELECT * FROM skin_scripts WHERE heroId = :heroId AND persistedStatus = 'INJECTED' LIMIT 1")
    suspend fun getActiveInjectedForHero(heroId: String): SkinScriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScript(script: SkinScriptEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScriptsIfAbsent(scripts: List<SkinScriptEntity>)

    @Query("UPDATE skin_scripts SET persistedStatus = :status, errorMessage = :errorMessage, lastUpdatedAt = :timestamp WHERE scriptId = :scriptId")
    suspend fun updateScriptStatus(
        scriptId: String,
        status: String,
        errorMessage: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM skin_scripts WHERE scriptId = :scriptId")
    suspend fun deleteScriptById(scriptId: String)

    @Query("SELECT * FROM backup_entries")
    fun observeAllBackupEntries(): Flow<List<BackupEntryEntity>>

    @Query("SELECT * FROM backup_entries WHERE scriptId = :scriptId")
    suspend fun getBackupEntriesForScript(scriptId: String): List<BackupEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackupEntries(entries: List<BackupEntryEntity>)

    @Query("DELETE FROM backup_entries WHERE scriptId = :scriptId")
    suspend fun deleteBackupEntriesForScript(scriptId: String)
}

@Database(
    entities = [SkinScriptEntity::class, BackupEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CompanionDatabase : RoomDatabase() {
    abstract fun companionDao(): CompanionDao

    companion object {
        @Volatile
        private var INSTANCE: CompanionDatabase? = null

        @Volatile
        private var instanceAppIdentity: Int = 0

        fun getInstance(context: Context): CompanionDatabase {
            val appContext = context.applicationContext
            val appIdentity = System.identityHashCode(appContext)
            val existing = INSTANCE
            if (existing != null && instanceAppIdentity == appIdentity && existing.isOpen) {
                return existing
            }
            return synchronized(this) {
                val syncedExisting = INSTANCE
                if (syncedExisting != null && instanceAppIdentity == appIdentity && syncedExisting.isOpen) {
                    syncedExisting
                } else {
                    Room.databaseBuilder(
                        appContext,
                        CompanionDatabase::class.java,
                        "mythic_script_companion.db",
                    ).build().also {
                        INSTANCE = it
                        instanceAppIdentity = appIdentity
                    }
                }
            }
        }
    }
}
