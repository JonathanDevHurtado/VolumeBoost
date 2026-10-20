# Decisiones de arquitectura (ADR)

Formato ligero: contexto → decisión → consecuencias. Las decisiones se pueden revisar; lo importante
es que quede escrito **por qué** se tomó cada una.

---

## ADR-001 — Sin AndroidX ni Compose: solo framework + Kotlin stdlib

**Contexto:** la app es una pantalla, un medidor y un servicio. El APK y el arranque importan en un
teléfono de gama media.

**Decisión:** usar `android.app.Activity`, vistas XML y `GaugeView` con `Canvas`. Cero dependencias
de UI.

**Consecuencias:**
- APK de cientos de KB y arranque rápido.
- Menos superficie de actualización y menos conflictos de versiones.
- Se pierden utilidades modernas (insets, lifecycle, back dispatcher) que hay que hacer a mano.
- **Revisar** si el proyecto crece a más pantallas: entonces Compose sí compensaría.

---

## ADR-002 — `LoudnessEnhancer` global con caída a modo por app

**Contexto:** Android no expone un «volumen 150 %». La única vía práctica es amplificar la señal.
`LoudnessEnhancer` sobre la sesión 0 afecta a toda la salida, pero está marcado como obsoleto y
algunos fabricantes lo ignoran.

**Decisión:** intentar primero la sesión 0; si falla, o si el usuario activa el modo compatible,
enganchar el efecto a las sesiones que anuncian las apps.

**Consecuencias:**
- Funciona en más dispositivos.
- Dos caminos que probar; el código debe impedir la doble ganancia.
- Offload de hardware y DRM siguen siendo un límite externo.

---

## ADR-003 — Servicio en primer plano con `specialUse`

**Contexto:** el efecto vive en el proceso; si Android lo mata, el refuerzo desaparece. Android 14
exige declarar un tipo de servicio en primer plano.

**Decisión:** `foregroundServiceType="specialUse"` con subtipo
`audio_gain_effect_keepalive`, arrancado solo cuando la UI está visible.

**Consecuencias:**
- El proceso sobrevive con la pantalla apagada.
- Google Play exige justificar `specialUse` en la ficha; es una app de audio, encaja.
- Si se publica en Play, revisar la política vigente antes de cada release.

---

## ADR-004 — Tonos generados por código, no archivos de audio

**Contexto:** los avisos deben ser cortos, variar con el nivel y no añadir licencias de audio.

**Decisión:** sintetizar PCM con `AudioTrack` en `Sfx` y `TestTone`.

**Consecuencias:**
- Cero assets y cero licencias de terceros.
- Se puede probar la forma de onda en JVM.
- Más código de audio que un simple `SoundPool`, pero totalmente controlado.

---

## ADR-005 — No persistir el porcentaje entre sesiones

**Contexto:** guardar «150 %» y restaurarlo al abrir la app puede sorprender al usuario con audio
amplificado (y potencialmente peligroso) sin que lo pida.

**Decisión:** el nivel vuelve a 100 % al reiniciar el proceso. Solo se persisten las preferencias
de sonido y de modo.

**Consecuencias:**
- Comportamiento predecible y seguro.
- Si se quiere persistencia, debe ser opt-in y con un aviso claro.

---

## ADR-006 — `targetSdk 35` con `fitsSystemWindows`

**Contexto:** Android 15 fuerza edge-to-edge para apps con target 35. La app usa una `ScrollView`
con `fitsSystemWindows="true"`.

**Decisión:** mantener el enfoque de framework y dejar que la vista raíz consuma los insets; el fondo
del tema cubre las barras transparentes.

**Consecuencias:**
- Menos código que un `OnApplyWindowInsetsListener` manual.
- **Verificar en dispositivo** que la zona inferior no queda tapada por la barra de navegación en
  gestos; si ocurre, añadir padding explícito.

---

## ADR-007 — El receptor de sesiones se registra como exportado

**Contexto:** las sesiones de audio las anuncian **otras apps**; un receptor no exportado no las
recibe en Android 13+.

**Decisión:** `RECEIVER_EXPORTED` y validar `sessionId > 0`.

**Consecuencias:**
- Funciona el modo por app.
- Superficie de ataque baja pero real: una app puede anunciar sesiones falsas. El efecto aplicado es
  la ganancia que el usuario eligió; no se ejecuta código ni se leen datos. Documentado en
  `SECURITY.md`.
