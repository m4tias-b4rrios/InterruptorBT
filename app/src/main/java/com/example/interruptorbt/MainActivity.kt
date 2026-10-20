package com.example.interruptorbt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * Menú principal: se elige qué papel cumple este equipo.
 *  - Modo Puente: el equipo que está conectado al Arduino.
 *  - Modo Control remoto: el equipo que controla la luz a distancia, a través del Puente.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.botonModoPuente).setOnClickListener {
            startActivity(Intent(this, PuenteActivity::class.java))
        }
        findViewById<Button>(R.id.botonModoControl).setOnClickListener {
            startActivity(Intent(this, ControlActivity::class.java))
        }
        // Vuelve a la pantalla del PIN
        findViewById<Button>(R.id.botonBloquear).setOnClickListener {
            startActivity(Intent(this, PinActivity::class.java))
            finish()
        }
    }
}
