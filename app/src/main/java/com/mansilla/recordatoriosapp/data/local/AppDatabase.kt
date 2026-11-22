package com.mansilla.recordatoriosapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local. Se crea una unica instancia en AppContainer.
 * El esquema se exporta a app/schemas para poder versionarlo y probar las migraciones.
 *
 * Version 2: agrega la columna ownerUid (cuenta duenia de cada registro).
 */
@Database(
    entities = [RecordatorioEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordatorioDao(): RecordatorioDao
}

/**
 * Migracion 1 -> 2. Los registros de la version 1 no tienen cuenta asociada: como no se puede
 * saber de quien son, se descartan para que ninguna cuenta nueva los vea por error.
 */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM recordatorios")
        db.execSQL("ALTER TABLE recordatorios ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recordatorios_ownerUid ON recordatorios (ownerUid)")
    }
}
