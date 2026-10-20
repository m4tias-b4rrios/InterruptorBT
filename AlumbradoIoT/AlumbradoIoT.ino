/*
  Alumbrado Exterior IoT - Arduino UNO + módulo Bluetooth HC-05
  -------------------------------------------------------------
  Es el mismo circuito de Tinkercad (LED en el pin 8 y LDR en A0)
  más el módulo HC-05 para comunicarse con el teléfono.

  Modo automático (por defecto): la LDR mide la luz ambiente.
    Si está oscuro enciende el LED; si hay luz lo apaga.

  Comandos que recibe (por Bluetooth o escritos en el Monitor Serie):
    '1' -> enciende la luz y pasa a modo manual
    '0' -> apaga la luz y pasa a modo manual
    'A' -> vuelve al modo automático
  ('1' y '0' son los mismos que envía la app InterruptorBT)

  Envía el estado cada 2 segundos y también apenas recibe un comando.
  Ejemplo:  LDR:523;LUZ:1;MODO:A
    LDR  = lectura del sensor (0 a 1023, más oscuro = número más bajo)
    LUZ  = 1 encendida / 0 apagada
    MODO = A automático / M manual

  Conexiones:
    LED:   pin 8 -> resistencia 220 ohm -> pata larga del LED; pata corta -> GND
    LDR:   5V -> LDR -> A0  y  A0 -> resistencia 10k -> GND
    HC-05: VCC -> 5V   GND -> GND
           TXD -> pin 10
           RXD <- pin 11 a través de un divisor de voltaje
                  (1k desde el pin 11 hasta RXD, y 2k desde RXD a GND),
                  porque el RXD del módulo trabaja a 3.3V y el Arduino entrega 5V
*/

#include <SoftwareSerial.h>

const int PIN_LUZ = 8;
const int PIN_LDR = A0;
const int PIN_RX_ARDUINO = 10;   // recibe lo que envía el HC-05 (su pin TXD)
const int PIN_TX_ARDUINO = 11;   // envía hacia el HC-05 (su pin RXD)

// Umbrales de oscuridad: ajústalos mirando los valores en el Monitor Serie.
// Son dos valores distintos para que el LED no parpadee cuando la luz
// ambiente queda justo en el límite.
const int UMBRAL_ENCENDER = 300;  // bajo este valor se considera de noche
const int UMBRAL_APAGAR   = 400;  // sobre este valor se considera de día

const unsigned long INTERVALO_ENVIO = 2000;  // milisegundos entre envíos de estado

SoftwareSerial bluetooth(PIN_RX_ARDUINO, PIN_TX_ARDUINO);

bool modoAutomatico = true;
bool luzEncendida = false;
unsigned long ultimoEnvio = 0;

void cambiarLuz(bool encender) {
  luzEncendida = encender;
  digitalWrite(PIN_LUZ, encender ? HIGH : LOW);
}

void enviarEstado(int valorLdr) {
  String estado = "LDR:" + String(valorLdr)
                + ";LUZ:" + String(luzEncendida ? 1 : 0)
                + ";MODO:" + String(modoAutomatico ? "A" : "M");
  bluetooth.println(estado);  // al teléfono
  Serial.println(estado);     // al Monitor Serie del computador
}

void procesarComando(char comando) {
  if (comando == '1') {
    modoAutomatico = false;
    cambiarLuz(true);
  } else if (comando == '0') {
    modoAutomatico = false;
    cambiarLuz(false);
  } else if (comando == 'A' || comando == 'a') {
    modoAutomatico = true;
  } else {
    return;  // cualquier otro carácter (por ejemplo el salto de línea) se ignora
  }
  // Responde de inmediato para que la app sepa que la orden llegó
  enviarEstado(analogRead(PIN_LDR));
}

void setup() {
  pinMode(PIN_LUZ, OUTPUT);
  cambiarLuz(false);          // parte apagada
  Serial.begin(9600);         // Monitor Serie
  bluetooth.begin(9600);      // velocidad por defecto del HC-05
}

void loop() {
  int valorLdr = analogRead(PIN_LDR);

  // 1) Comandos que llegan del teléfono o del Monitor Serie
  if (bluetooth.available()) {
    procesarComando(bluetooth.read());
  }
  if (Serial.available()) {
    procesarComando(Serial.read());
  }

  // 2) En modo automático la LDR decide
  if (modoAutomatico) {
    if (!luzEncendida && valorLdr < UMBRAL_ENCENDER) {
      cambiarLuz(true);
    } else if (luzEncendida && valorLdr > UMBRAL_APAGAR) {
      cambiarLuz(false);
    }
  }

  // 3) Cada 2 segundos informa el estado (monitoreo)
  if (millis() - ultimoEnvio >= INTERVALO_ENVIO) {
    ultimoEnvio = millis();
    enviarEstado(valorLdr);
  }
}
