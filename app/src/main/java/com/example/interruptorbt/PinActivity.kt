package com.example.interruptorbt

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pantalla de seguridad (primera pantalla de la app).
 *
 * Medidas de seguridad (control de acceso y manejo de credenciales, ISO/IEC 27400):
 *  - Sin el PIN no se puede llegar a los controles de la luz.
 *  - El PIN nunca se guarda tal cual: se guarda su hash SHA-256 combinado con una "sal" aleatoria.
 *  - Después de 3 intentos fallidos la app se bloquea 30 segundos.
 */
class PinActivity : AppCompatActivity() {

    companion object {
        private const val ARCHIVO = "seguridad"
        private const val CLAVE_HASH = "pin_hash"
        private const val CLAVE_SAL = "pin_sal"
        private const val CLAVE_INTENTOS = "intentos_fallidos"
        private const val CLAVE_BLOQUEO = "bloqueado_hasta"
        private const val MAX_INTENTOS = 3
        private const val TIEMPO_BLOQUEO_MS = 30_000L
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var titulo: TextView
    private lateinit var campoPin: EditText
    private lateinit var campoConfirmar: EditText
    private lateinit var mensaje: TextView
    private lateinit var boton: Button

    // true = todavía no existe un PIN y hay que crearlo
    private var creandoPin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        prefs = getSharedPreferences(ARCHIVO, MODE_PRIVATE)
        titulo = findViewById(R.id.tituloPin)
        campoPin = findViewById(R.id.campoPin)
        campoConfirmar = findViewById(R.id.campoConfirmar)
        mensaje = findViewById(R.id.mensajePin)
        boton = findViewById(R.id.botonPin)

        creandoPin = !prefs.contains(CLAVE_HASH)
        if (creandoPin) {
            titulo.text = "Crea un PIN de 4 números"
            campoConfirmar.visibility = View.VISIBLE
            boton.text = "Guardar PIN"
        } else {
            titulo.text = "Ingresa tu PIN"
            campoConfirmar.visibility = View.GONE
            boton.text = "Entrar"
        }

        boton.setOnClickListener {
            if (creandoPin) crearPin() else verificarPin()
        }
    }

    private fun crearPin() {
        val pin = campoPin.text.toString()
        val confirmacion = campoConfirmar.text.toString()

        if (!pin.matches(Regex("\\d{4}"))) {
            mensaje.text = "El PIN debe tener exactamente 4 números"
            return
        }
        if (pin != confirmacion) {
            mensaje.text = "Los dos PIN no coinciden"
            return
        }

        val sal = generarSal()
        prefs.edit()
            .putString(CLAVE_SAL, sal)
            .putString(CLAVE_HASH, calcularHash(pin, sal))
            .putInt(CLAVE_INTENTOS, 0)
            .apply()
        entrar()
    }

    private fun verificarPin() {
        val ahora = System.currentTimeMillis()
        val bloqueadoHasta = prefs.getLong(CLAVE_BLOQUEO, 0L)
        if (ahora < bloqueadoHasta) {
            val segundos = (bloqueadoHasta - ahora + 999) / 1000
            mensaje.text = "Demasiados intentos. Espera $segundos segundos"
            return
        }

        val pin = campoPin.text.toString()
        val sal = prefs.getString(CLAVE_SAL, "") ?: ""
        val hashGuardado = prefs.getString(CLAVE_HASH, "") ?: ""

        if (calcularHash(pin, sal) == hashGuardado) {
            prefs.edit().putInt(CLAVE_INTENTOS, 0).apply()
            entrar()
            return
        }

        val intentos = prefs.getInt(CLAVE_INTENTOS, 0) + 1
        if (intentos >= MAX_INTENTOS) {
            prefs.edit()
                .putInt(CLAVE_INTENTOS, 0)
                .putLong(CLAVE_BLOQUEO, ahora + TIEMPO_BLOQUEO_MS)
                .apply()
            mensaje.text = "PIN incorrecto. App bloqueada por 30 segundos"
        } else {
            prefs.edit().putInt(CLAVE_INTENTOS, intentos).apply()
            mensaje.text = "PIN incorrecto. Te quedan ${MAX_INTENTOS - intentos} intentos"
        }
        campoPin.text.clear()
    }

    /** SHA-256 de (sal + PIN), escrito en hexadecimal. */
    private fun calcularHash(pin: String, sal: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest((sal + pin).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 16 bytes aleatorios en hexadecimal. Hace que el mismo PIN tenga un hash distinto en cada equipo. */
    private fun generarSal(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun entrar() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
