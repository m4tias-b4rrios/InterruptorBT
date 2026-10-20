package com.example.interruptorbt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * MODO CONTROL REMOTO: este equipo se conecta por Bluetooth al equipo Puente.
 *  - Envía las órdenes '1' (encender), '0' (apagar) y 'A' (automático) al Puente.
 *  - Muestra el estado de la luz que el Puente le reenvía desde el Arduino.
 */
@SuppressLint("MissingPermission")
class ControlActivity : BaseBluetoothActivity() {

    @Volatile private var socketPuente: BluetoothSocket? = null
    @Volatile private var salidaPuente: OutputStream? = null
    private val bloqueo = Any()

    @Volatile private var pantallaAbierta = true
    @Volatile private var nombrePuente = ""

    private lateinit var textoConexion: TextView
    private lateinit var botonConectar: Button
    private var conectando = false   // true mientras se está conectando (solo se usa en la pantalla)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        textoConexion = findViewById(R.id.textoConexion)
        configurarPanelControl()

        botonConectar = findViewById(R.id.botonConectarPuente)
        botonConectar.setOnClickListener {
            conBluetoothListo {
                elegirDispositivo("Elige el equipo que está en Modo Puente") { conectarPuente(it) }
            }
        }
    }

    private fun conectarPuente(dispositivo: BluetoothDevice) {
        if (conectando) return            // evita abrir dos conexiones con un doble toque
        conectando = true
        botonConectar.isEnabled = false
        textoConexion.text = "Conectando…"
        Thread {
            // Si había una conexión anterior, se cierra
            val anterior = synchronized(bloqueo) {
                val s = socketPuente
                socketPuente = null
                salidaPuente = null
                s
            }
            try {
                anterior?.close()
            } catch (e: IOException) {
            }

            try {
                val socket = dispositivo.createRfcommSocketToServiceRecord(UUID_APP)
                socket.connect()
                synchronized(bloqueo) {
                    socketPuente = socket
                    salidaPuente = socket.outputStream
                }
                nombrePuente = dispositivo.name ?: dispositivo.address
                runOnUiThread {
                    textoConexion.text = "Conectado al Puente ($nombrePuente). Esperando datos del Arduino…"
                    botonConectar.isEnabled = true
                    conectando = false
                }
                leerPuente(socket)
            } catch (e: Exception) {
                runOnUiThread {
                    textoConexion.text = "Desconectado"
                    botonConectar.isEnabled = true
                    conectando = false
                    toast("No se pudo conectar. ¿El otro equipo tiene la app abierta en Modo Puente?")
                }
            }
        }.start()
    }

    /** Lee lo que envía el Puente hasta que se corta la conexión. */
    private fun leerPuente(socket: BluetoothSocket) {
        try {
            val lector = BufferedReader(InputStreamReader(socket.inputStream))
            while (true) {
                val linea = lector.readLine()?.trim() ?: break
                if (linea == "ERROR:SIN_ARDUINO") {
                    runOnUiThread {
                        textoConexion.text = "Conectado al Puente ($nombrePuente), pero el Puente no está conectado al Arduino"
                    }
                    continue
                }
                val estado = EstadoLuz.desdeLinea(linea) ?: continue
                runOnUiThread {
                    textoConexion.text = "Conectado al Puente ($nombrePuente)"
                    mostrarEstado(estado)
                }
            }
        } catch (e: IOException) {
            // se cortó la conexión con el Puente
        }

        val eraLaActual = synchronized(bloqueo) {
            if (socketPuente === socket) {
                socketPuente = null
                salidaPuente = null
                true
            } else {
                false
            }
        }
        try {
            socket.close()
        } catch (e: IOException) {
        }
        if (eraLaActual && pantallaAbierta) {
            runOnUiThread { textoConexion.text = "Desconectado del Puente" }
        }
    }

    /** Envía la orden al Puente, que se la pasa al Arduino. */
    override fun enviarComando(comando: Char) {
        val resultado = synchronized(bloqueo) {
            val salida = salidaPuente
            if (salida == null) {
                "sin_conexion"
            } else {
                try {
                    salida.write(comando.code)
                    salida.flush()
                    "ok"
                } catch (e: IOException) {
                    "error"
                }
            }
        }
        when (resultado) {
            "sin_conexion" -> toast("Primero conéctate al Puente")
            "error" -> toast("Se perdió la conexión con el Puente")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pantallaAbierta = false
        try {
            socketPuente?.close()
        } catch (e: IOException) {
        }
    }
}
