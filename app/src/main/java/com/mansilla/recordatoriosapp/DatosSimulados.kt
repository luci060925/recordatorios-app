package com.mansilla.recordatoriosapp

val recordatorios: List<Recordatorio> = listOf(
    Recordatorio(
        id = 1,
        titulo = "Estudiar Kotlin",
        descripcion = "Repasar las sealed classes",
        categoria = Categoria.Estudio,
        prioridad = Prioridad.Alta,
        estado = EstadoRecordatorio.Pendiente
    ),
    Recordatorio(
        id = 2,
        titulo = "Comprar alimentos",
        descripcion = "Ir al supermercado",
        categoria = Categoria.Personal,
        prioridad = Prioridad.Media,
        estado = EstadoRecordatorio.Completado
    ),
    Recordatorio(
        id = 3,
        titulo = "Entregar informe",
        descripcion = null,                  // recordatorio SIN descripcion
        categoria = Categoria.Trabajo,
        prioridad = Prioridad.Alta,
        estado = EstadoRecordatorio.Pendiente
    ),
    Recordatorio(
        id = 4,
        titulo = "Organizar escritorio",
        descripcion = "Ordenar papeles y cables",
        categoria = Categoria.Personal,
        prioridad = Prioridad.Baja,
        estado = EstadoRecordatorio.Cancelado
    )
)
// Set: categorias sin repetir (Personal aparece dos veces en la lista)
val categoriasUnicas: Set<Categoria> = recordatorios
    .map { it.categoria }
    .toSet()
// Map: color asignado a cada categoria
val coloresCategorias: Map<Categoria, String> = mapOf(
    Categoria.Estudio to "Azul",
    Categoria.Trabajo to "Rojo",
    Categoria.Personal to "Verde"
)
// Filtros
val recordatoriosPendientes: List<Recordatorio> = recordatorios.filter {
    it.estado == EstadoRecordatorio.Pendiente
}
val recordatoriosAltaPrioridad: List<Recordatorio> = recordatorios.filter {
    it.prioridad == Prioridad.Alta
}