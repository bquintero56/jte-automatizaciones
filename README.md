# Automatizaciones Jenkins (JTE)

Un solo repositorio con tres carpetas, una por pieza de JTE: **configuración**, **librerías** y **templates**.
En Jenkins (*Manage Jenkins → Jenkins Templating Engine*, o en la carpeta del job) apunta cada fuente a este
repo con su **Base Directory**:

| Fuente JTE | Base Directory |
|---|---|
| Pipeline Configuration / Templates | `jte-pipeline-configurations-automations` y `jte-pipelines-template` |
| Library Source | `jte-automations-pipeline-libraries` |

```
jte-pipeline-configurations-automations/
├── validar-archivos/pipeline_config.groovy            Pipeline 1 (ruta general + rutas específicas por horario)
├── validar-archivos-horarios/pipeline_config.groovy   Pipeline 2 (validaciones + SIN_APROBADO + ENVIOCC)
├── validar-rutas-especificas/pipeline_config.groovy   Pipeline 3 (archivo del día en 4 rutas específicas)
└── consultar-servicios/pipeline_config.groovy         Pipeline 4 (consultas REST/SOAP, esperan HTTP 200)

jte-automations-pipeline-libraries/
├── validacion_archivos/                               Librería del pipeline 1
│   ├── library_config.groovy
│   └── steps/
│       ├── programar_validaciones.groovy
│       └── validar_archivos.groovy
├── validacion_horarios/                               Librería del pipeline 2
│   ├── library_config.groovy
│   └── steps/
│       ├── programar_ejecuciones.groovy
│       ├── validar_archivos_horario.groovy
│       ├── procesar_sin_aprobado.groovy
│       └── procesar_enviocc.groovy
├── validacion_rutas/                                  Librería del pipeline 3
│   ├── library_config.groovy
│   └── steps/
│       ├── programar_validacion_rutas.groovy
│       └── validar_rutas.groovy
├── consultas_servicios/                               Librería del pipeline 4
│   ├── library_config.groovy
│   └── steps/
│       ├── programar_consultas.groovy
│       └── ejecutar_consultas.groovy
└── notificacion_correo/                               Compartida por los cuatro pipelines
    ├── library_config.groovy
    └── steps/
        ├── enviar_correo_validacion_archivos.groovy   Correo del pipeline 1
        ├── enviar_correo_resultados.groovy            Correo del pipeline 2 (3 secciones)
        ├── enviar_correo_validacion_rutas.groovy      Correo del pipeline 3
        └── enviar_correo_consultas.groovy             Correo del pipeline 4

jte-pipelines-template/
├── validar-archivos/validar_archivos.groovy
├── validar-archivos-horarios/validar_archivos_horarios.groovy
├── validar-rutas-especificas/validar_rutas_especificas.groovy
└── consultar-servicios/consultar_servicios.groovy
```

## Antes de ejecutar

1. Ajusta en los `pipeline_config.groovy`: IP, usuarios, rutas, destinatarios, y en el pipeline 2 los
   nombres, `COM_ID` y `DES` reales de cada archivo.
2. El nombre de cada carpeta de librería debe coincidir con su bloque en la config
   (`validacion_archivos`, `validacion_horarios`, `notificacion_correo`).
3. En el servidor remoto, permiso en `sudoers` (`visudo -f /etc/sudoers.d/svc_jenkins`):
   `svc_jenkins ALL=(usr_datos) NOPASSWD: /usr/bin/test, /usr/bin/ls, /usr/bin/cat`
4. Plugins: Email Extension (con SMTP configurado). El nodo de `jenkins_agent_label` debe tener el SSH.
   Pipeline 4: `curl` en el nodo, plugin Credentials Binding y las credenciales de cada servicio creadas en Jenkins
   (el ID va en `credencial`; nunca tokens ni contraseñas en la config, el repo es público).
5. Haz commit y push del repo y lanza cada job una vez a mano: es lo que registra el cron.
   El pipeline 2 corre a diario a las 15:00 hora de Chile (`hora` y `zonaHoraria` en su config); cualquier ejecución
   manual valida todo, sin esperar a esa hora.

## Pruebas de fecha

En el pipeline 2, `diasAtras` (0 = hoy, 1 = ayer) mueve la fecha de las tres tareas. Déjalo en 0 en producción.
