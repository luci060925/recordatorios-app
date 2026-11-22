package com.mansilla.recordatoriosapp

import com.mansilla.recordatoriosapp.data.local.CodigoSync
import com.mansilla.recordatoriosapp.data.mapper.toDomain
import com.mansilla.recordatoriosapp.data.mapper.toDto
import com.mansilla.recordatoriosapp.data.mapper.toEntityOrNull
import com.mansilla.recordatoriosapp.data.remote.RecordatorioDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MapeosTest {

    private fun dto(
        id: String? = "id-1", titulo: String? = "Titulo", categoria: String? = "TRABAJO",
        prioridad: String? = "ALTA", estado: String? = "COMPLETADO"
    ) = RecordatorioDto(id, titulo, "Desc", categoria, prioridad, estado, 100L, 200L)

    @Test
    fun dtoValido_seTransformaEnEntidadSincronizada() {
        val e = dto().toEntityOrNull(uid = "ana", ahora = 5L)!!
        assertEquals("id-1", e.id)
        assertEquals("ana", e.ownerUid)
        assertEquals(CodigoSync.SINCRONIZADO, e.estadoSync)
        assertNull(e.operacionPendiente)
        assertEquals(100L, e.fecha)
        assertEquals(200L, e.actualizadoEn)
    }

    @Test
    fun dtoSinIdOSinTitulo_seDescarta() {
        assertNull(dto(id = null).toEntityOrNull("ana", 0))
        assertNull(dto(id = " ").toEntityOrNull("ana", 0))
        assertNull(dto(titulo = null).toEntityOrNull("ana", 0))
        assertNull(dto(titulo = "  ").toEntityOrNull("ana", 0))
    }

    @Test
    fun valoresDesconocidos_usanPredeterminados() {
        val dominio = dto(categoria = "RARA", prioridad = null, estado = "???").toEntityOrNull("ana", 0)!!.toDomain()
        assertEquals(Categoria.Personal, dominio.categoria)
        assertEquals(Prioridad.Media, dominio.prioridad)
        assertEquals(EstadoRecordatorio.Pendiente, dominio.estado)
    }

    @Test
    fun entidadADominioYDto_conservanLosDatos() {
        val entidad = dto().toEntityOrNull("ana", 0)!!
        val dominio = entidad.toDomain()
        assertEquals(Categoria.Trabajo, dominio.categoria)
        assertEquals(Prioridad.Alta, dominio.prioridad)
        assertEquals(EstadoRecordatorio.Completado, dominio.estado)
        assertEquals(EstadoSync.SINCRONIZADO, dominio.estadoSync)

        val vuelta = entidad.toDto()
        assertEquals("TRABAJO", vuelta.categoria)
        assertEquals("ALTA", vuelta.prioridad)
        assertEquals("COMPLETADO", vuelta.estado)
        assertNotNull(vuelta.id)
    }

    @Test
    fun estadoDeSyncDesconocido_seTrataComoPendiente() {
        val entidad = dto().toEntityOrNull("ana", 0)!!.copy(estadoSync = "RARO")
        assertEquals(EstadoSync.PENDIENTE, entidad.toDomain().estadoSync)
    }
}
