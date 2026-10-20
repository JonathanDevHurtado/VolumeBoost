# Arquitectura de Volume Boost

> Documento para quien va a mantener el proyecto. Explica **por qué** el código está dividido así,
> qué invariantes no se pueden romper y dónde mirar cuando algo falla.

## 1. Vista general

```text
┌─────────────────────────────────────────────────────────────────────┐
│                         MainActivity (UI)                           │
│  botones · gestos de mantener · avisos · permisos · sonido de prueba │
└───────────────┬───────────────────────────────────┬─────────────────┘
                │ setPercent / setMode              │ addListener
                ▼                                   │
┌───────────────────────────────────────────────────┴─────────────────┐
│                         BoostEngine (singleton)                     │
│  percent · mode · globalState · sesiones · LoudnessEnhancer         │
│  receptor de ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION         │
└───────────────┬───────────────────────────────────┬─────────────────┘
                │ estado                            │ startForeground
                ▼                                   ▼
┌─────────────────────────────┐       ┌───────────────────────────────┐
│  GaugeView · Sfx · TestTone │       │  BoostService (1er plano)     │
│  dibujo y audio de la UI     │       │  mantiene vivo el proceso     │
└─────────────────────────────┘       └───────────────────────────────┘
```

No hay capa de red, base de datos ni inyección de dependencias. El proyecto es deliberadamente
pequeño: **una app de audio de un solo proceso y un solo hilo de UI**.

## 2. Módulos y responsabilidades

| Archivo | Responsabilidad | No debe hacer |
|---|---|---|
| `BoostMath.kt` | Matemática pura: clamp, pasos, dB, frecuencia | Tocar Android |
| `BoostEngine.kt` | Ciclo de vida de los efectos de audio | Dibujar ni mostrar UI |
| `BoostService.kt` | Mantener el proceso vivo y la notificación | Reproducir audio |
| `MainActivity.kt` | Estado de UI, gestos, permisos, preferencias | Crear efectos directamente |
| `GaugeView.kt` | Dibujo del medidor | Guardar estado de negocio |
| `Sfx.kt` | Tonos cortos sintetizados | Sonar si `enabled == false` |
| `TestTone.kt` | Arpegio en bucle para probar el refuerzo | Seguir sonando en segundo plano |

## 3. Invariantes

Estas reglas son la base de que el audio no se multiplique ni se quede «pegado»:

1. **Un solo camino de ganancia por audio.** `usePerApp()` decide entre global y por sesión; nunca
   se configuran ambos sobre la misma salida.
2. **`percent == 100` implica cero efectos vivos.** `releaseEffects()` libera el efecto global y
   todas las sesiones, y desregistra el receptor.
3. **El servicio no se detiene de inmediato al bajar a 100 %.** Se espera `IDLE_STOP_DELAY_MS`
   (2,5 s) y se vuelve a comprobar `percent`; así se evita la carrera con un `+` inmediato.
4. **`startForeground()` se llama al principio de `onStartCommand()`.** Android 8+ mata el proceso
   si un servicio en primer plano no lo hace en unos 5 s.
5. **El estado mutable de `BoostEngine` se toca solo en el hilo principal.** Actividad, servicio y
   receptores se ejecutan en el main thread; no hay locks porque no hay concurrencia real.
6. **El servicio no hace audio.** Solo protege el proceso; toda la lógica de audio está en el
   engine. Esto permite probar la matemática sin Android y razonar sobre el ciclo de vida por
   separado.

## 4. Modelo de estado

```kotlin
percent: Int            // 100..150, siempre múltiplo conceptual (la UI puede dibujar decimales)
mode: GLOBAL|PER_APP    // preferencia del usuario
globalState: IDLE|ACTIVE|UNAVAILABLE
serviceAlive: Boolean   // lo mantiene BoostService
uiVisible: Boolean      // onStart..onStop de MainActivity
tracked: Set<Int>       // sesiones anunciadas o del sonido de prueba
enhancers: Map<Int, LoudnessEnhancer>
```

### Transiciones clave

```text
100 --setPercent(>100)--> crea efecto(s) --> applyGain()
>100 --setPercent(100)--> releaseEffects() --> notifica --> servicio espera 2,5 s --> stopSelf()

GLOBAL + creación fallida --> globalState=UNAVAILABLE --> bindAllTracked() (modo por app)
GLOBAL activo + configure() falla --> libera global --> UNAVAILABLE --> bindAllTracked()
```

## 5. Secuencias

### Subir el volumen con la app abierta

```text
Usuario pulsa +5
  MainActivity.stepTo(105)
    BoostEngine.setPercent(105)
      ensureEffects()  (si venía de 100)
        LoudnessEnhancer(0) + setTargetGain(42 mB)
      notifyListeners()
    MainActivity.render() + servicio se asegura
```

### Pasar a segundo plano

```text
onStop()
  testTone.stop()
  unregisterVolumeReceiver()
  BoostEngine.removeListener(this)
  uiVisible = false
```

El servicio sigue vivo y conserva el efecto. Si el sistema mata el servicio, `onDestroy()` apaga el
refuerzo cuando la UI no está visible (nadie protegería el proceso).

### Sesión de audio de otra app (modo por app)

```text
App de música envía ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION (sessionId)
  BoostEngine.receiver.onReceive()
    trackSession(sessionId)
      bind(sessionId) si percent > 100 y usePerApp()
        LoudnessEnhancer(sessionId).setTargetGain(...)
```

Al cerrar la sesión se libera el efecto y se quita de `tracked`.

## 6. Hilos y ciclo de vida

- **Main thread:** toda la UI, `BoostEngine`, `BoostService`, receptores y `Sfx`.
- **`Handler` de la UI:** `holdRunnable` cada 16 ms y la parada diferida del servicio.
- **AudioTrack:** los tonos se escriben en modo estático; no hay hilos de audio propios.
- **Proceso:** un solo proceso. No hay `:remote`.

No se usan corrutinas porque no hay trabajo de E/S ni operaciones largas. Añadirlas sin necesidad
sería complejidad gratuita.

## 7. Manejo de errores

| Fallo | Detección | Respuesta |
|---|---|---|
| `LoudnessEnhancer` no soportado | excepción al crear | `globalState=UNAVAILABLE`, modo por app, texto en UI |
| Efecto liberado por el sistema | `configure()` devuelve `false` | se libera, se pasa a por app |
| Sesión ya cerrada | fallo al configurar | se descarta la sesión |
| `startForeground` falla | excepción capturada | se registra y la UI avisa; el refuerzo sigue con la app abierta |
| `registerReceiver` falla | excepción capturada | se registra; el modo global sigue funcionando |
| `AudioTrack` no disponible | excepción o `write <= 0` | no suena; no es crítico |

## 8. Dónde extender

- **Nuevo modo de audio:** añade un `enum` y una rama en `BoostEngine`, nunca dupliques la lógica de
  `applyGain`.
- **Nuevo idioma:** crea `values-xx/strings.xml`; el layout usa recursos.
- **Nueva métrica visual:** `GaugeView` es autónomo; no metas lógica de negocio en `onDraw`.
- **Persistencia del nivel:** si se añade, debe restaurarse **después** de `setMode` y **antes** de
  `ensureService`, y solo si el usuario lo pidió (hoy se decide no persistir para no sorprender con
  audio amplificado al abrir la app).

## 9. Deuda técnica conocida

- `MainActivity` concentra UI y coordinación; si crece, separar en un `BoostController`.
- No hay pruebas instrumentadas (requieren dispositivo/emulador).
- El receptor de sesiones es `RECEIVER_EXPORTED` por necesidad del sistema; un emisor malicioso
  podría anunciar sesiones falsas. El impacto se limita a aplicar la ganancia elegida por el usuario
  a una sesión que no es suya. Está documentado en `SECURITY.md`.
