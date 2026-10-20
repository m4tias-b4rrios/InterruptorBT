package com.example.interruptorbt

/**
 * Estado del sistema de alumbrado, tal como lo informa el Arduino.
 * El Arduino envía cada 2 segundos una línea con este formato:
 *     LDR:523;LUZ:1;MODO:A
 */
data class EstadoLuz(
    val ldr: Int,               // lectura del sensor de luz (0 a 1023)
    val luzEncendida: Boolean,  // LUZ:1 = encendida, LUZ:0 = apagada
    val automatico: Boolean     // MODO:A = automático, MODO:M = manual
) {
    companion object {
        /** Convierte una línea del Arduino en un EstadoLuz. Si la línea no tiene el formato esperado, devuelve null. */
        fun desdeLinea(linea: String): EstadoLuz? {
            val datos = linea.trim().split(";").mapNotNull { parte ->
                val claveValor = parte.split(":")
                if (claveValor.size == 2) claveValor[0].trim() to claveValor[1].trim() else null
            }.toMap()

            val ldr = datos["LDR"]?.toIntOrNull() ?: return null
            val luz = datos["LUZ"] ?: return null
            val modo = datos["MODO"] ?: return null
            return EstadoLuz(ldr, luz == "1", modo == "A")
        }
    }
}
