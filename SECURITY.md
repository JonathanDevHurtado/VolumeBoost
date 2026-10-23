# Política de seguridad

## Versiones soportadas

| Versión | Soporte |
|---|---|
| 1.1.x | Sí |
| 1.0.x | Solo correcciones críticas |
| < 1.0 | No |

## Cómo reportar una vulnerabilidad

**No abras un issue público.** Envía un correo a **JonathanHurtadoDev@proton.me** con:

- versión de la app y de Android;
- modelo de dispositivo (si aplica);
- pasos para reproducir;
- impacto esperado (qué consigue un atacante);
- prueba de concepto, si la tienes.

Si quieres cifrar el mensaje, indícalo y se acordará un canal.

## Plazos orientativos

| Etapa | Plazo |
|---|---|
| Acuse de recibo | 72 h |
| Evaluación inicial | 7 días |
| Corrección / mitigación | según gravedad, se acuerda contigo |
| Divulgación | coordinada, después de publicar la corrección |

## Alcance

Entran en alcance:

- el código de la app y sus scripts de build;
- el manifest y los permisos;
- la lógica de audio y el servicio en primer plano;
- la cadena de suministro del build (dependencias, wrapper, CI).

Quedan fuera de alcance:

- vulnerabilidades del sistema operativo o del fabricante;
- apps de terceros que ignoran los efectos de audio;
- ingeniería social;
- denegación de servicio local que requiera acceso físico al dispositivo.

## Prácticas del proyecto

- Sin secretos en el repositorio (keystore, `local.properties`, `.env`).
- Sin permiso de Internet ni telemetría.
- CI con JDK y dependencias fijadas.
- Revisión de cambios que toquen audio, permisos o ciclo de vida del servicio.

Gracias por ayudar a que el proyecto sea más seguro.
