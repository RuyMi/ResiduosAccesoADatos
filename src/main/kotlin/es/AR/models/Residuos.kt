package es.AR.models

import java.time.LocalDate

data class Residuos(
    val año: Short,
    val mes: String,
    val lote: Short = 0,
    val residuos: String = "Desconocido",
    val num_distrito: Short = 0,
    val nombre_distrito: String = "Desconocido",
    val toneladas: Float = 0.0f
    ) {


}