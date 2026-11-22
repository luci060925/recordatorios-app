package com.mansilla.recordatoriosapp

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mansilla.recordatoriosapp.data.local.AppDatabase
import com.mansilla.recordatoriosapp.data.local.MIGRACION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Migracion de la base de la version 1 (sin cuentas) a la 2 (con ownerUid), sobre una base real. */
@RunWith(AndroidJUnit4::class)
class MigracionRoomTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migra1a2_agregaOwnerUidYDescartaLosRegistrosSinCuenta() {
        val nombre = "prueba-migracion"

        helper.createDatabase(nombre, 1).apply {
            execSQL(
                "INSERT INTO recordatorios (id, titulo, descripcion, categoria, prioridad, estado, fecha, " +
                    "actualizadoEn, version, estadoSync, operacionPendiente, ultimoError) " +
                    "VALUES ('viejo-1', 'Dato de la version 1', NULL, 'ESTUDIO', 'ALTA', 'PENDIENTE', 1, 1, 1, " +
                    "'SINCRONIZADO', NULL, NULL)"
            )
            close()
        }

        // runMigrationsAndValidate compara el esquema resultante con el exportado (schemas/2.json).
        val db = helper.runMigrationsAndValidate(nombre, 2, true, MIGRACION_1_2)

        // Los registros de la version 1 no tienen cuenta: se descartan para no mostrarlos a otra persona.
        db.query("SELECT COUNT(*) FROM recordatorios").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }

        // La columna existe y las filas nuevas pueden asociarse a una cuenta.
        db.execSQL(
            "INSERT INTO recordatorios (id, ownerUid, titulo, descripcion, categoria, prioridad, estado, fecha, " +
                "actualizadoEn, version, estadoSync, operacionPendiente, ultimoError) " +
                "VALUES ('nuevo-1', 'ana', 'Dato nuevo', NULL, 'ESTUDIO', 'ALTA', 'PENDIENTE', 1, 1, 1, " +
                "'PENDIENTE', 'CREAR', NULL)"
        )
        db.query("SELECT ownerUid FROM recordatorios WHERE id = 'nuevo-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("ana", it.getString(0))
        }
    }
}
