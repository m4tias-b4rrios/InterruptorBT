package com.example.interruptorbt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * MODO PUENTE: este equipo está conectado al Arduino (módulo HC-05).
 *
 *   Control remoto  --Bluetooth-->  PUENTE  --Bluetooth-->  Arduino
 *   Control remoto  <--estado-----  PUENTE  <--estado-----  Arduino
 *
 *  - Recibe las órdenes del Control remoto ('1', '0', 'A') y se las pasa al Arduino.
 *  - Recibe el estado del Arduino (LDR:...;LUZ:...;MODO:...), lo muestra y se lo reenvía al Control remoto.
 *  - También se puede controlar la luz desde este mismo equipo.
 */
@SuppressLint("MissingPermission")
class PuenteActivity : BaseBluetoothActivity() {

    // Conexión con el Arduino
    @Volatile private var socketArduino: BluetoothSocket? = null
    @Volatile private var salidaArduino: OutputStream? = null
    private val bloqueoArduino = Any()

    // Conexión con el Control remoto
    @Volatile private var servidor: BluetoothServerSocket? = null
    @Volatile private var salidaControl: OutputStream? = null
    @Volatile private var socketControl: BluetoothSocket? = null
    private val bloqueoControl = Any()

    // Último estado recibido del Arduino (se envía al Control remoto apenas se conecta)
    @Volatile private var ultimaLineaArduino: String? = null

    @Volatile private var pantallaAbierta = true
    private var servidorIniciado = false

    private lateinit var textoArduino: TextView
    private lateinit var textoControl: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_puente)
        // Evita que la pantalla se apague mientras este equipo hace de puente
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        textoArduino = findViewById(R.id.textoArduino)
        textoControl = findViewById(R.id.textoControl)
        configurarPanelControl()

        findViewById<Button>(R.id.botonConectarArduino).setOnClickListener {
            conBluetoothListo {
                elegirDispositivo("Elige el módulo del Arduino (HC-05)") { conectarArduino(it) }
            }
        }

        // Apenas se abre esta pantalla, queda esperando al Control remoto
        conBluetoothListo { iniciarServidor() }
    }

    // ---------- Arduino ----------

    private fun conectarArduino(dispositivo: BluetoothDevice) {
        textoArduino.text = "Arduino: conectando…"
        Thread {
            // Si había una conexión anterior, se cierra
            val anterior = synchronized(bloqueoArduino) {
                val s = socketArduino
                socketArduino = null
                salidaArduino = null
                s
            }
            try {
                anterior?.close()
            } catch (e: IOException) {
            }

            try {
                val socket = dispositivo.createRfcommSocketToServiceRecord(UUID_ARDUINO)
                socket.connect()
                synchronized(bloqueoArduino) {
                    socketArduino = socket
                    salidaArduino = socket.outputStream
                }
                val nombre = dispositivo.name ?: dispositivo.address
                runOnUiThread { textoArduino.text = "Arduino: conectado ($nombre)" }
                leerArduino(socket)
            } catch (e: Exception) {
                runOnUiThread {
                    textoArduino.text = "Arduino: desconectado"
                    toast("No se pudo conectar al Arduino. ¿Está encendido?")
                }
            }
        }.start()
    }

    /** Lee las líneas de estado del Arduino hasta que se corta la conexión. */
    private fun leerArduino(socket: BluetoothSocket) {
        try {
            val lector = BufferedReader(InputStreamReader(socket.inputStream))
            while (true) {
                val linea = lector.readLine() ?: break
                val estado = EstadoLuz.desdeLinea(linea) ?: continue
                ultimaLineaArduino = linea.trim()
                runOnUiThread { mostrarEstado(estado) }
                enviarAlControl(linea.trim())   // se reenvía al Control remoto
            }
        } catch (e: IOException) {
            // se cortó la conexión con el Arduino
        }

        val eraLaActual = synchronized(bloqueoArduino) {
            if (socketArduino === socket) {
                socketArduino = null
                salidaArduino = null
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
            runOnUiThread { textoArduino.text = "Arduino: desconectado" }
        }
    }

    /** Envía una orden al Arduino. Se usa con los botones de esta pantalla y con las órdenes del Control remoto. */
    override fun enviarComando(comando: Char) {
        val enviado = synchronized(bloqueoArduino) {
            val salida = salidaArduino
            if (salida == null) {
                false
            } else {
                try {
                    salida.write(comando.code)
                    salida.flush()
                    true
                } catch (e: IOException) {
                    false
                }
            }
        }
        if (!enviado) {
            enviarAlControl("ERROR:SIN_ARDUINO")   // avisa al Control remoto
            runOnUiThread { toast("El Arduino no está conectado") }
        }
    }

    // ---------- Control remoto ----------

    /**
     * Queda siempre esperando conexiones del Control remoto.
     * Si se conecta un control nuevo, reemplaza al anterior. Así una conexión vieja
     * que quedó cortada nunca bloquea a la nueva.
     */
    private fun iniciarServidor() {
        if (servidorIniciado) return
        servidorIniciado = true
        Thread {
            try {
                val s = adaptador?.listenUsingRfcommWithServiceRecord(NOMBRE_SERVICIO, UUID_APP)
                    ?: return@Thread
                servidor = s
                runOnUiThread { textoControl.text = "Control remoto: esperando conexión…" }
                while (pantallaAbierta) {
                    val cliente = s.accept()   // espera aquí hasta que un Control remoto se conecta
                    val anterior = synchronized(bloqueoControl) {
                        val viejo = socketControl
                        socketControl = cliente
                        salidaControl = cliente.outputStream
                        viejo
                    }
                    cerrar(anterior)   // si había otro control, se desconecta
                    Thread { atenderControl(cliente) }.start()
                }
            } catch (e: Exception) {
                if (pantallaAbierta) {
                    runOnUiThread { textoControl.text = "Control remoto: no disponible" }
                }
            }
        }.start()
    }

    /** Recibe las órdenes de un Control remoto y se las pasa al Arduino. */
    private fun atenderControl(cliente: BluetoothSocket) {
        val nombre = cliente.remoteDevice?.name ?: cliente.remoteDevice?.address ?: "sin nombre"
        runOnUiThread { textoControl.text = "Control remoto: conectado ($nombre)" }

        // Apenas se conecta, recibe el último estado conocido del Arduino
        val ultima = ultimaLineaArduino
        if (ultima != null) enviarAlControl(ultima)

        try {
            val entrada = cliente.inputStream
            while (true) {
                val dato = entrada.read()
                if (dato == -1) break
                val comando = dato.toChar()
                if (comando == '1' || comando == '0' || comando == 'A') {
                    val orden = when (comando) {
                        '1' -> "Encender"
                        '0' -> "Apagar"
                        else -> "Auto"
                    }
                    runOnUiThread {
                        textoControl.text = "Control remoto: conectado ($nombre)\nÚltima orden recibida: $orden"
                    }
                    enviarComando(comando)
                }
            }
        } catch (e: IOException) {
            // el Control remoto se desconectó
        }
        soltarControl(cliente)
    }

    /** Libera la conexión con un Control remoto que se desconectó. */
    private fun soltarControl(cliente: BluetoothSocket) {
        val eraElActual = synchronized(bloqueoControl) {
            if (socketControl === cliente) {
                socketControl = null
                salidaControl = null
                true
            } else {
                false
            }
        }
        cerrar(cliente)
        if (eraElActual && pantallaAbierta) {
            runOnUiThread { textoControl.text = "Control remoto: esperando conexión…" }
        }
    }

    /** Envía una línea al Control remoto, si hay uno conectado. Si la conexión está cortada, la libera. */
    private fun enviarAlControl(linea: String) {
        val cortado = synchronized(bloqueoControl) {
            val salida = salidaControl ?: return
            try {
                salida.write((linea + "\n").toByteArray())
                salida.flush()
                null
            } catch (e: IOException) {
                socketControl
            }
        }
        if (cortado != null) soltarControl(cortado)
    }

    private fun cerrar(socket: BluetoothSocket?) {
        try {
            socket?.close()
        } catch (e: IOException) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pantallaAbierta = false
        try {
            servidor?.close()
        } catch (e: IOException) {
        }
        try {
            socketControl?.close()
        } catch (e: IOException) {
        }
        try {
            socketArduino?.close()
        } catch (e: IOException) {
        }
    }
}
