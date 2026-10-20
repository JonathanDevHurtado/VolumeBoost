# Cómo funciona Volume Boost

## La idea

Android no deja a una app subir el volumen del sistema más allá de su máximo. Lo que sí permite es
**amplificar la señal digital** con un efecto de audio: `android.media.audiofx.LoudnessEnhancer`.

| Medidor | Amplitud | Ganancia (`LoudnessEnhancer`) |
|--------:|---------:|------------------------------:|
| 100 %   | ×1,00    | 0 mB (sin efecto)             |
| 125 %   | ×1,25    | 194 mB (+1,9 dB)              |
| 150 %   | ×1,50    | 352 mB (+3,5 dB)              |

Fórmula: `mB = 2000 · log10(porcentaje / 100)` (ver `BoostMath.gainMillibels`, con tests).

Para que "100 %" signifique realmente el máximo del teléfono, el **volumen multimedia del sistema
debe estar al máximo**. La app lo muestra y ofrece un botón; no lo sube sola para no dar un salto
brusco (sobre todo con audífonos).

## Dos modos de aplicar el efecto

1. **Global (por defecto):** `LoudnessEnhancer(0)`, enganchado a la mezcla de salida. Afecta a todo
   lo que suena. Es lo que usan casi todas las apps "volume booster".
2. **Compatible por app:** Android considera obsoletos los efectos sobre la sesión 0 y algunos
   fabricantes los ignoran. En este modo la app escucha el aviso estándar
   `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` que envían los reproductores compatibles y engancha un `LoudnessEnhancer` a cada sesión. También cubre el sonido de prueba.

Si el modo global falla al crearse, la app pasa sola al modo por app y lo indica en pantalla.
**Nunca se combinan** sobre el mismo audio (la ganancia se multiplicaría).

Qué puede seguir sin funcionar: reproducción por *offload* de hardware (el DSP del chip decodifica y
salta los efectos de software), apps con salida protegida/DRM, y llamadas. Lo único que se puede
hacer es el modo por app o reproducir con otra app/ajuste.

## Piezas

```
MainActivity ──escucha/ordena──▶ BoostEngine ◀──escucha── BoostService
   │  (botones, mantener,          (LoudnessEnhancer         (mantiene vivo el proceso,
   │   sonidos, avisos)             global/por app,           notificación, se apaga
   │                                receptor de sesiones)     solo tras 100 %)
   ├── GaugeView (medidor)
   ├── Sfx (avisos)        TestTone (sonido de prueba)
   └── BoostMath (cálculo puro)
```

Reglas que evitan errores:

- **Todo corre en el hilo principal** (actividad, servicio, receptores): `BoostEngine` no necesita
  cerrojos. Está documentado en su cabecera.
- **El servicio no hace audio**: solo evita que Android mate el proceso mientras hay refuerzo
  (`foregroundServiceType="specialUse"`, requerido en Android 14+). Llama a `startForeground()` en
  cuanto arranca, como exige Android, y **nunca se detiene al instante** al volver a 100 %: espera
  2,5 s por si vuelves a subir (evita la carrera "arrancar y parar a la vez").
- **A 100 % se liberan los efectos**, así que sin refuerzo la app no gasta CPU ni toca el audio.
- Si el servicio muere con la app abierta, la pantalla lo vuelve a levantar; si muere con la app
  cerrada, el refuerzo se apaga (nadie protegería el proceso).
- Las notificaciones se agrupan (máx. ~3 por segundo) porque al mantener presionado el valor cambia
  10 veces por segundo y Android descarta las demasiado seguidas.

## Mantener presionado

`MainActivity.holdRunnable` corre cada 16 ms. Calcula un valor decimal con el tiempo real
transcurrido (`HOLD_RATE = 10` puntos/s, con tope de 100 ms por fotograma por si el hilo se
atrasa), mueve la aguja con ese decimal (movimiento fluido) y solo cambia el efecto cuando el entero
cambia. Un segundo dedo en el otro botón se ignora mientras hay uno activo. Si la app pasa a segundo
plano mientras mantienes, se cancela en silencio.

## Sonidos de aviso

Generados por código (seno con ataque de 4 ms y fundido, sin "clics"), sin archivos de audio:

| Momento                       | Sonido                                          |
|-------------------------------|-------------------------------------------------|
| Cada 5 % al mantener subiendo | clic de 40 ms (más agudo cuanto más alto)       |
| Al soltar / pulsar −, +, atajo| bip de 170 ms con el tono del nivel alcanzado   |
| Llegar al 150 %               | campanita de 3 notas + vibración corta          |
| Volver a 100 %                | dos notas descendentes                          |

Tono: 600 Hz (100 %) a 1300 Hz (150 %). Amplitud moderada para que, al pasar por el propio
refuerzo (×1,5), no sature.

## Optimización para Helio G99 Ultra y similares

Sin ficha técnica mágica: lo que ayuda de verdad en este chip (2× Cortex-A76 + 6× A55, Mali-G57,
pantalla de 120 Hz) es **hacer poco**:

- **Cero librerías externas** (ni AndroidX ni Compose): APK de unos cientos de KB, arranque rápido.
- **Medidor propio** con `Paint`, `RectF` y degradado creados una vez; `onDraw` no reserva memoria,
  así que no hay pausas del recolector de basura a 120 fps.
- **Efecto de audio de bajo coste** (un solo `LoudnessEnhancer`); se libera al volver a 100 %.
- **R8 + recorte de recursos** en `release`; solo el ícono adaptable (sin PNG por densidad).
- No hay código nativo, así que no hay nada específico de ABI que optimizar.

## Revisión de errores (lista de lo comprobado)

| Riesgo                                                      | Cómo se cubre                                       |
|-------------------------------------------------------------|-----------------------------------------------------|
| `LoudnessEnhancer` rechazado por el fabricante              | `try/catch`, estado `UNAVAILABLE`, caída a modo por app |
| Efecto global muerto a mitad (audioserver reinicia)         | `configure()` falla → se libera y se pasa a por app |
| Ganancia doble (global + por sesión)                        | `usePerApp()` excluye uno u otro                     |
| Sesiones de audio ya cerradas                               | fallo al configurar → se descartan                   |
| `startForeground` tarde/ausente (crash Android 8+)          | primera línea de `onStartCommand`; try/catch         |
| Arrancar y parar el servicio a la vez                       | parada diferida 2,5 s y re-chequeo de `percent`      |
| `registerReceiver` sin flag en Android 13+                  | `RECEIVER_EXPORTED` / `NOT_EXPORTED` según el caso   |
| Dos botones de mantener a la vez                            | un solo `holdDir` activo                             |
| Mantener presionado y salir de la app                       | `cancelHold()` en `onPause`                          |
| Sonido de prueba sonando en segundo plano                   | se detiene en `onStop`                               |
| Fuga de `AudioTrack`                                        | liberación diferida + `Sfx.release()` + tope de 8    |
| Rotación de pantalla                                        | el estado vive en `BoostEngine`; la UI se repinta    |
| Límites 100 / 150                                           | `BoostMath.clamp` en todos los caminos + tests       |
| Cadenas con `%` rompen `aapt2`                              | `formatted="false"` donde no hay argumentos          |

**Estado de verificación:** el proyecto compila y las pruebas JVM pasan (ver
[`VERIFICACION.md`](VERIFICACION.md)). Lo que sigue sin verificar es el resultado audible en un
Redmi real, el comportamiento con audífonos Bluetooth y las apps con DRM/offload. Para eso existe el
sonido de prueba, la guía de problemas y el checklist manual.
