package com.example.interruptorbt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Código que comparten las pantallas Puente y Control remoto:
 * permiso de Bluetooth, encender el Bluetooth, elegir un equipo vinculado
 * y el panel con el estado de la luz y los botones Encender / Apagar / Automático.
 */
@SuppressLint("MissingPermission")
abstract class BaseBluetoothActivity : AppCompatActivity() {

    companion object {
        // Perfil serie (SPP). Es el que usa el módulo HC-05 del Arduino.
        val UUID_ARDUINO: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Identificador propio de esta app, para la conexión entre los dos equipos Android.
        val UUID_APP: UUID = UUID.fromString("5f2d7c1e-8a4b-4c3d-9e6f-a1b2c3d4e5f6")
        const val NOMBRE_SERVICIO = "AlumbradoIoT"
    }

    protected var adaptador: BluetoothAdapter? = null

    // Lo que se ejecuta cuando el permiso y el Bluetooth están listos
    private var accionPendiente: (() -> Unit)? = null

    private val pedirPermiso =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) prepararBluetooth() else toast("Se necesita el permiso de Bluetooth")
        }

    private val pedirActivarBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (adaptador?.isEnabled == true) prepararBluetooth() else toast("Hay que encender el Bluetooth")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        adaptador = manager.adapter
    }

    /** Pide el permiso y enciende el Bluetooth si hace falta; cuando todo está listo ejecuta [accion]. */
    protected fun conBluetoothListo(accion: () -> Unit) {
        accionPendiente = accion
        prepararBluetooth()
    }

    private fun prepararBluetooth() {
        if (!tienePermiso()) {
            pedirPermiso.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        val a = adaptador
        if (a == null) {
            toast("Este equipo no tiene Bluetooth")
            return
        }
        if (!a.isEnabled) {
            pedirActivarBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        val accion = accionPendiente
        accionPendiente = null
        accion?.invoke()
    }

    // En Android 12 o superior hay que pedir BLUETOOTH_CONNECT. En versiones anteriores basta con el Manifest.
    private fun tienePermiso(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    /** Muestra la lista de equipos vinculados en Ajustes > Bluetooth y devuelve el que se elige. */
    protected fun elegirDispositivo(titulo: String, alElegir: (BluetoothDevice) -> Unit) {
        val lista = adaptador?.bondedDevices?.toList().orEmpty()
        if (lista.isEmpty()) {
            toast("No hay equipos vinculados. Vincúlalos primero en Ajustes > Bluetooth")
            return
        }
        val nombres = lista.map { "${it.name ?: "Sin nombre"}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setItems(nombres) { _, i -> alElegir(lista[i]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Panel de control (layout panel_control.xml) ----------

    /** Conecta los botones Encender / Apagar / Automático con [enviarComando]. */
    protected fun configurarPanelControl() {
        findViewById<Button>(R.id.botonEncender).setOnClickListener { enviarComando('1') }
        findViewById<Button>(R.id.botonApagar).setOnClickListener { enviarComando('0') }
        findViewById<Button>(R.id.botonAutomatico).setOnClickListener { enviarComando('A') }
    }

    /** Cada pantalla decide hacia dónde se envía la orden ('1' encender, '0' apagar, 'A' automático). */
    protected abstract fun enviarComando(comando: Char)

    /** Muestra en pantalla el último estado recibido del Arduino. */
    protected fun mostrarEstado(estado: EstadoLuz) {
        findViewById<View>(R.id.indicadorLuz).setBackgroundResource(
            if (estado.luzEncendida) R.drawable.boton_on else R.drawable.boton_off
        )
        findViewById<TextView>(R.id.textoLuz).text =
            if (estado.luzEncendida) "Luz: ENCENDIDA" else "Luz: APAGADA"
        findViewById<TextView>(R.id.textoModo).text =
            if (estado.automatico) "Modo: automático (sensor de luz)" else "Modo: manual (desde la app)"
        findViewById<TextView>(R.id.textoLdr).text = "Sensor de luz: ${estado.ldr} de 1023"
    }

    protected fun toast(mensaje: String) = Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
}
