package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.local.dao.DocumentDao
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentType

class Converters {
    @TypeConverter
    fun fromDocumentType(type: DocumentType): String = type.name

    @TypeConverter
    fun toDocumentType(name: String): DocumentType = try {
        DocumentType.valueOf(name)
    } catch (_: Exception) {
        DocumentType.OTHER
    }
}

@Database(entities = [DocumentEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "habib_editor.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
