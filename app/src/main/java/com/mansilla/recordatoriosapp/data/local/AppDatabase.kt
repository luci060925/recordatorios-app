package com.mansilla.recordatoriosapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base de datos local. Se crea una unica instancia en AppContainer.
 * El esquema se exporta a app/schemas para poder versionarlo y migrarlo.
 */
@Database(
    entities = [RecordatorioEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordatorioDao(): RecordatorioDao
}
