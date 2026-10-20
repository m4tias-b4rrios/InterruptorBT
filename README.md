# Sistema de Alumbrado Exterior Automatizado con IoT

App Android (Kotlin, Android Studio) + Arduino UNO con sensor de luz (LDR) y módulo Bluetooth HC-05.
Asignatura TI3042 Aplicaciones Móviles para IoT, Unidad 2.

## Cómo funciona

La misma app se instala en **dos equipos Android**. Al entrar se elige el papel de cada uno:

```
  Equipo 1: CONTROL REMOTO  ──Bluetooth──►  Equipo 2: PUENTE  ──Bluetooth──►  Arduino + HC-05
           (órdenes 1 / 0 / A)                                                (LED y sensor LDR)
  Equipo 1: CONTROL REMOTO  ◄──estado────   Equipo 2: PUENTE  ◄──estado────   Arduino
           (LDR:523;LUZ:1;MODO:A)
```

- **Modo Puente**: el equipo conectado al Arduino. Pasa las órdenes del Control remoto al Arduino y le reenvía al Control remoto el estado que informa el Arduino. También permite controlar la luz desde el mismo equipo.
- **Modo Control remoto**: controla la luz a distancia, a través del Puente, y muestra el estado (monitoreo).

Órdenes: `1` encender, `0` apagar, `A` volver al modo automático (la luz depende del sensor).
Estado que envía el Arduino cada 2 segundos y después de cada orden: `LDR:<0-1023>;LUZ:<1|0>;MODO:<A|M>`.

## Contenido

| Ruta | Qué es |
|---|---|
| `app/src/main/java/com/example/interruptorbt/PinActivity.kt` | Pantalla de PIN (seguridad) |
| `.../MainActivity.kt` | Menú: Modo Puente o Modo Control remoto |
| `.../BaseBluetoothActivity.kt` | Código común: permisos, Bluetooth, elegir equipo vinculado, panel de control |
| `.../PuenteActivity.kt` | Modo Puente (conexión con el Arduino y con el Control remoto) |
| `.../ControlActivity.kt` | Modo Control remoto |
| `.../EstadoLuz.kt` | Lee las líneas de estado del Arduino |
| `app/src/main/res/layout/` | Pantallas (XML) |
| `AlumbradoIoT/AlumbradoIoT.ino` | Programa del Arduino |

## Herramientas

- **Android Studio + Kotlin**, que es el lenguaje oficial recomendado por Google para Android.
- **Vistas XML con AppCompat y Material Components**, el estándar de diseño de apps Android, compatible desde Android 8.0 (API 26).
- **API de Bluetooth de Android** (`BluetoothAdapter`, `BluetoothSocket`, perfil serie RFCOMM/SPP), sin librerías externas.
- **Arduino IDE** para el programa del Arduino (`SoftwareSerial` para el HC-05).

## Por qué Bluetooth y no WiFi

- Es una conexión directa entre los equipos, a corta distancia, como un control de alumbrado del mismo lugar. No depende de un router ni de internet.
- El HC-05 es Bluetooth y es económico. El Arduino UNO no tiene WiFi.
- Solo se pueden conectar equipos que fueron vinculados (emparejados) antes con una clave.

## Seguridad (ISO/IEC 27400)

| Medida | Dónde |
|---|---|
| **Control de acceso con PIN**: sin el PIN no se llega a los controles de la luz | `PinActivity.kt` |
| **El PIN no se guarda en texto plano**: se guarda su hash SHA-256 con una sal aleatoria (`SecureRandom`) | `PinActivity.kt` |
| **Bloqueo por intentos fallidos**: 3 errores bloquean la app 30 segundos | `PinActivity.kt` |
| **Pantallas internas no exportadas**: ninguna otra app puede abrir los controles saltándose el PIN | `AndroidManifest.xml` |
| **Sin respaldo automático** (`allowBackup="false"`): el hash del PIN no sale del equipo | `AndroidManifest.xml` |
| **Conexión Bluetooth segura**: solo con equipos vinculados; la conexión RFCOMM segura va cifrada | `createRfcommSocketToServiceRecord` y `listenUsingRfcommWithServiceRecord` |
| **Permisos mínimos**: solo `BLUETOOTH_CONNECT` (no pide ubicación ni búsqueda de equipos) | `AndroidManifest.xml` |

Si se olvida el PIN: Ajustes del teléfono → Aplicaciones → Alumbrado IoT → Almacenamiento → Borrar datos. Al abrir la app se crea uno nuevo.

## Cómo usarla

1. Arma el circuito y sube `AlumbradoIoT/AlumbradoIoT.ino` al Arduino.
2. Vincula en **Ajustes → Bluetooth**:
   - el equipo Puente con el **HC-05** (clave 1234 o 0000),
   - el equipo Puente con el equipo Control remoto (se confirma el mismo código en los dos).
3. **Equipo Puente**: abre la app → PIN → **Modo Puente** → **Conectar Arduino (HC-05)**. Deja esta pantalla abierta.
4. **Equipo Control remoto**: abre la app → PIN → **Modo Control remoto** → **Conectar al Puente** → elige el equipo Puente.
5. Usa **Encender**, **Apagar** y **Auto**. El estado se actualiza en los dos equipos.

## Conexiones del Arduino

| Componente | Conexión |
|---|---|
| LED | pin 8 → resistencia 220 Ω → LED → GND |
| LDR | 5V → LDR → A0, y A0 → resistencia 10 kΩ → GND |
| HC-05 VCC / GND | 5V / GND |
| HC-05 TXD | pin 10 |
| HC-05 RXD | pin 11 a través de un divisor de voltaje (1 kΩ desde el pin 11, 2 kΩ a GND) |
