# Contexto del proyecto: automatizaciones Jenkins con JTE

Idioma de trabajo: **español** (comentarios, logs, mensajes de error y correos).

## 1. Qué es esto

Dos pipelines de **monitoreo** que corren en Jenkins con **Jenkins Templating Engine (JTE)**. Se conectan por SSH a un
servidor remoto, comprueban que existan archivos (CSV y PDF) en ciertas rutas y envían **un correo HTML** con el resultado.

- **Pipeline 1 – `validar-archivos`**: verifica que 5 archivos existan en una ruta general y también en su ruta específica.
- **Pipeline 2 – `validar-archivos-horarios`**: tres tareas en un mismo job (10 validaciones de archivos, `SIN_APROBADO` y
  `ENVIOCC`) con **un único correo** al final.
- **Pipeline 3 – `validar-rutas-especificas`**: busca el archivo del día (ej. `testSiniestro_<fecha>.zip`) en 4 rutas específicas y envía
  un correo con `OK` / `ERROR` por ruta (ver §5B).

Regla general: **un archivo faltante NO debe poner el pipeline en rojo**. Se informa por correo y en el log.

## 2. Arquitectura JTE (1 repo, 3 carpetas)

Todo vive en el repo `jte-automatizaciones`; en Jenkins cada fuente JTE apunta a su carpeta con *Base Directory*.

| Carpeta | Qué contiene | Quién lo usa |
|---|---|---|
| `jte-pipeline-configurations-automations` | Una carpeta por automatización con su `pipeline_config.groovy`: **solo valores** (IP, usuarios, rutas, archivos, formatos, destinatarios). Sin lógica. | Todos los steps vía `config` |
| `jte-automations-pipeline-libraries` | Una carpeta por librería con `library_config.groovy` (valida los campos) y `steps/*.groovy` (la **lógica**). | El template |
| `jte-pipelines-template` | Una carpeta por automatización con el template `.groovy` que solo **orquesta**: llama a los steps en orden. | Jenkins |

### Cómo se enlazan las piezas

- El **nombre del bloque** en `pipeline_config.groovy` = **nombre de la carpeta** de la librería (`validacion_archivos`,
  `validacion_horarios`, `notificacion_correo`). Si difiere una letra, JTE no la carga.
- El **nombre del archivo** de un step es el nombre con que se llama en el template (`validar_archivos.groovy` → `validar_archivos()`).
- Dentro de un step, `config` es el bloque de configuración de **esa** librería (`config.host`, `config.sinAprobado.textoFijo`...).
- `execution.time`, `execution.units` y `jenkins_agent_label` vienen de la sección `keywords` de la configuración.
- **Todo campo de la config debe estar declarado en `library_config.groovy`** o JTE falla con
  `field 'x' is not used`. Los bloques anidados (`sinAprobado { }`, `enviocc { }`) se declaran anidados también.

### Estructura de directorios

```
jte-pipeline-configurations-automations/
├── validar-archivos/pipeline_config.groovy
├── validar-archivos-horarios/pipeline_config.groovy
└── validar-rutas-especificas/pipeline_config.groovy

jte-automations-pipeline-libraries/
├── validacion_archivos/                      (pipeline 1)
│   ├── library_config.groovy
│   └── steps/
│       ├── programar_validaciones.groovy     cron (10:15 general + horas específicas) + parámetro HORARIO
│       └── validar_archivos.groovy           valida según el horario (PAUSADO no se busca)
├── validacion_horarios/                      (pipeline 2)
│   ├── library_config.groovy
│   └── steps/
│       ├── programar_ejecuciones.groovy      cron diario (config.hora)
│       ├── validar_archivos_horario.groovy   las 10 validaciones
│       ├── procesar_sin_aprobado.groovy      tarea SIN_APROBADO
│       └── procesar_enviocc.groovy           tarea ENVIOCC
├── validacion_rutas/                         (pipeline 3)
│   ├── library_config.groovy
│   └── steps/
│       ├── programar_validacion_rutas.groovy cron diario (config.hora)
│       └── validar_rutas.groovy              busca el archivo del día en cada ruta
└── notificacion_correo/                      (compartida)
    ├── library_config.groovy
    └── steps/
        ├── enviar_correo_validacion_archivos.groovy   correo del pipeline 1
        ├── enviar_correo_resultados.groovy            correo del pipeline 2 (3 secciones)
        └── enviar_correo_validacion_rutas.groovy      correo del pipeline 3

jte-pipelines-template/
├── validar-archivos/validar_archivos.groovy
├── validar-archivos-horarios/validar_archivos_horarios.groovy
└── validar-rutas-especificas/validar_rutas_especificas.groovy
```

## 3. Convenciones técnicas (respetarlas al modificar)

1. **Nada quemado en los steps.** Todo valor sale de `config`. Un campo nuevo se agrega en la config **y** en `library_config.groovy`.
2. **SSH sin credenciales de Jenkins.** El nodo `jenkins_agent_label` ya tiene el acceso SSH, así que no se usa `sshagent` ni `credentialsId`.
   Comando base: `ssh -o StrictHostKeyChecking=no <user>@<host> "sudo -n -u <runAsUser> <comando>"`.
   - `user` (`svc_jenkins`) es con quien se entra; `runAsUser` (`usr_datos`) es quien puede ver los archivos.
   - `sudo -n` evita que el job se cuelgue pidiendo contraseña (falla con `rc=1`).
   - Sudoers en el servidor: `svc_jenkins ALL=(usr_datos) NOPASSWD: /usr/bin/test, /usr/bin/ls, /usr/bin/cat`.
     Se evita `find` a propósito (permitirlo en sudoers abre ejecución arbitraria con `-exec`).
3. **Búsqueda con comodín.** `svc_jenkins` no puede expandir `*` en esas carpetas, así que se lista la carpeta y se filtra en el nodo
   con un `case` de shell. `buscar(ruta, patron)` usa **una sola conexión SSH**: `sudo test -d <ruta> && sudo ls -1 <ruta>` volcado a
   `buscar_listado.txt` en el workspace; si el `rc` no es 0 (no existe, sin permiso, sudo pidió contraseña o falló el SSH) devuelve `null`.
   Si no, filtra el archivo local y devuelve la lista de coincidencias (vacía si no hay). Está duplicada en cada step.
4. **Log limpio.** Los `sh` con `while` usan `"""#!/bin/sh` pegado a las comillas y sin sangría: Jenkins ejecuta `sh` con `-x` y, sin el
   shebang, el log se inunda de `IFS= / read -r f / case "$f" in`. Las continuaciones de línea dentro de ese bloque son `\\`.
5. **Fechas con `date` en el nodo**, con la zona horaria de la config (`TZ='America/Bogota' date -d 'N days ago' +'<formato>'`).
   No se calculan en Groovy (evita problemas de sandbox y de zona horaria del controller).
6. **Estados, no excepciones.** Los steps devuelven listas/mapas con el resultado. `estadoSiFaltan` (`SUCCESS` | `UNSTABLE` | `FAILURE`)
   decide el color del build en las validaciones de archivos: se aplica con `unstable(...)` o `currentBuild.result = 'FAILURE'`,
   **nunca con `error`**, para que el pipeline siga y el correo se envíe. Un error técnico en `SIN_APROBADO` o `ENVIOCC` se captura y se
   convierte en `estado: 'ERROR_PROCESO'` + `unstable(...)`.
7. **Estructura de los templates:** `timeout(execution)` → `node(jenkins_agent_label)` → `withFolderProperties` → `try/catch/finally`,
   con `clean_job_workspace()` en el `finally`. Cada tarea es un `stage`. Todo `catch (Exception e)` va precedido de
   `catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) { throw e }`, para que un aborto manual o el `timeout`
   detengan el job (estado ABORTED) en vez de tratarse como error.
8. **Correo** con `emailext` (plugin Email Extension), `mimeType: 'text/html'`.
   - Destinatarios en `notificacion_correo.destinatarios`; **copia (CC)** en `notificacion_correo.copia` (opcional, separados por coma, cada
     pipeline tiene la suya). Los dos steps de correo arman `to` = destinatarios + cada copia con prefijo `cc:` (sintaxis de emailext).
   - Asunto: `<asuntoPrefijo> <NOMBRES SIN FECHA NI .csv, separados por coma> <asuntoSufijo> - <fecha>`
     (ej. `Monitoreo activo Seguimiento DEVOLUCION, CONTADOR 360 - 2026-10-09`).
   - Cuerpo: `Estimados buenos días,` → contenido → `Saludos.` (la "NOTA IMPORTANTE" que se mencionó al inicio **no** va en el correo).
   - Tabla con `OK` en verde y `ERROR` en rojo.

## 4. Pipeline 1 – `validar-archivos`

**Objetivo:** verificar 5 archivos (nombre con la fecha del **día de ejecución**, `diasAtras = 0`) en dos sitios, **cada uno en su horario**
(hora de Chile, `zonaHoraria = 'America/Santiago'`):
1. **Ruta general** (`rutaGeneral`, ej. `/data/entrada`) a las **`horaRutaGeneral` (10:15)**: nombre exacto, `REPORTE_{fecha}.csv`.
   Se validan **siempre los 5** archivos (no admite pausa).
2. **Ruta específica** de cada archivo (`rutaEspecifica`) a su **`horaEspecifica`** (hoy 11:15 o 18:45): nombre con comodín
   (`patronEspecifico`, ej. `REPORTE_{fecha}_*.csv`), porque en esa ruta el nombre lleva números aleatorios.
   Con `estado = 'PAUSADO'` (monitoreo pausado, no se generan archivos) **no se busca** y sale `PAUSADO`; con `'ACTIVO'` se busca.

**Un correo por ejecución** (3 al día) con lo validado en ese horario. `PAUSADO` no cuenta como faltante (no cambia el color del build).

- **Config (`validacion_archivos`):** `host`, `user`, `runAsUser`, `formatoFecha` (`%Y%m%d`), `zonaHoraria`, `diasAtras`, `estadoSiFaltan`,
  `horaRutaGeneral`, `rutaGeneral`, `archivos` (lista de
  `[nombre, rutaEspecifica, patronEspecifico, horaEspecifica, estado]`).
- **Ejecución:** `programar_validaciones()` (primera línea del template) valida horas y estados y registra el cron con las horas únicas
  (`horaRutaGeneral` + cada `horaEspecifica`) en `zonaHoraria`, más el parámetro **`HORARIO` solo para pruebas** (vacío = automático, lo
  que usa el cron; `TODOS` = ruta general y todas las específicas; `HH:mm` = simula ese horario).
- **Horario de la ejecución:** la **última hora programada que ya pasó** (`hora actual >= hora`; si Jenkins arranca un minuto tarde,
  11:16 sigue siendo 11:15). Antes de la primera hora del día no valida ni envía correo. Sin ventana de tolerancia (`ventanaMinutos` se quitó).
- **Step `validar_archivos(modo)`** devuelve `[fecha, horario, descripcion, resultados]`; por archivo: `[proceso, archivo, estatus, rutas, fecha]`.
  `rutas` = solo las rutas validadas en este horario (general, específica o ambas con `TODOS`), cada una con su estatus `OK` / `ERROR` /
  `PAUSADO` (una ruta no accesible es `ERROR` y el motivo queda en el log).
- **Correo (`enviar_correo_validacion_archivos(validacion)`):** asunto `… - <fecha> <horario>`; texto "validación de las 11:15 - rutas
  específicas". Tabla `PROCESO | RUTA | ESTATUS` con **una fila por ruta**; la celda `PROCESO` ocupa las filas de su archivo (`rowspan`).
  Colores: `OK` verde, `ERROR` rojo, `PAUSADO` naranja.

## 5. Pipeline 2 – `validar-archivos-horarios`

Un solo job, **una sola ejecución diaria a las 15:00 hora de Chile** (`America/Santiago`) que corre las tres tareas, y **un solo correo al final**.

### 5.1 Las 10 validaciones (`validar_archivos_horario`)

- Busca cada archivo en `rutaBase` con `{mes}` = `AAAAMM` del día de ejecución (ej. `/Proyecto/M/BACKUP/202610`; en noviembre pasa solo a `202611`).
- Nombre buscado: `prefijoNombre + nombre` con `{fecha}` reemplazada (ej. `*_DEVOLUCION_2026-10-09.csv`). Todos los archivos llevan
  números aleatorios al inicio, por eso `prefijoNombre = '*_'`.
- Cada archivo de la config lleva: `COM_ID` (ID numérico), `DES` (descripción) y `nombre`. Opcional: `ruta` para sobrescribir `rutaBase`.
- Si hay varios que coinciden, usa el **más reciente** (`ls -1t`). El encontrado se copia al workspace con su nombre original (`sudo cat`)
  para **adjuntarlo al correo**.
- Resultado por archivo: `[comId, descripcion, proceso, archivo, estatus, adjunto, fecha]` (`adjunto` = nombre del archivo copiado, o `null`).

### 5.2 `SIN_APROBADO` (`procesar_sin_aprobado`)

1. Busca el CSV `*_SIN_APROBADO_<AAAA-MM-DD>.csv` en `rutaBase/{mes}`. Si **no existe** → estado `CSV_NO_ENCONTRADO`, se informa por correo y la
   tarea se detiene (el pipeline no falla). Si existe y todo está bien, no se envía nada por esta tarea.
   Si hay **varios** CSV, usa el **más reciente** por fecha de modificación (`ls -1t`); el log muestra todos.
2. Copia el CSV al workspace (`sudo cat`) y **elimina las filas** cuya columna `TIPO_LIQUIDADOR` valga `Liquidador externo`
   (sin distinguir mayúsculas, ignorando espacios y comillas). Genera la copia limpia **con el mismo nombre del original** (ej. `482913_SIN_APROBADO_2026-10-15.csv`), que **se adjunta al correo** (no se archiva en Jenkins).
   **El original en el servidor no se modifica** (por ahora, solo prueba).
3. Busca los PDF `*_SIN_APROBADO_*_<AAAAMMDD>.pdf` en la misma ruta y cuenta cuántos hay.
4. De cada nombre toma el **número del medio** (lo que queda entre `_SIN_APROBADO_` y `_<fecha>.pdf`) y comprueba que exista en la columna
   `NUMERO_SINIESTRO` del CSV. Los que no estén se reportan en el correo con una tabla `NÚMERO | PDF`.
- Config (`sinAprobado { }`): `textoFijo`, `formatoFechaCsv`, `formatoFechaPdf`, `separador` (hoy `;`), `columnaTipo`, `valorExcluir`, `columnaSiniestro`.
- Estados: `OK`, `PDF_SIN_REGISTRO`, `CSV_NO_ENCONTRADO`, `ERROR_PROCESO`.

### 5.3 `ENVIOCC` (`procesar_enviocc`)

1. Busca el CSV `*_ENVIOCC_<AAAA-MM-DD>.csv` en `rutaBase/{mes}`. Si no existe → `CSV_NO_ENCONTRADO` (correo, sin fallar).
   Si hay **varios**, usa el **más reciente** por fecha de modificación (`buscar()` de este step lista con `ls -1t`); el log muestra todos.
2. Copia el CSV al workspace **con su nombre original** (se adjunta al correo, `ec.adjunto`) y extrae todos los valores de la columna `FILENAMEFIELD`.
3. Busca en `rutaPdf` (`/Sistema/Proyecto/test/PDF`) los PDF `CCTEST_*_<dd_MM_yyyy>.pdf` (empiezan con `textoPdf`; fecha **día_mes_año** al final; número del medio aleatorio).
4. **Normaliza** ambos lados quitando los números iniciales y la extensión: `2131231232_CCTEST_123213123213_09_10_2026.pdf` → `CCTEST_123213123213_09_10_2026`.
5. Compara los dos conjuntos:
   - **Faltan PDF** (están en la columna, no hay PDF) → acción: **generar ticket (faltan PDF)**.
   - **Sobran PDF** (hay PDF, no están en la columna) → acción: **generar ticket solicitando el CSV para diferenciar**.
- En el correo aparece como **una fila más de la tabla** (`COM_ID 67`, `DES`): `OK` si todo coincide, `ERROR` en cualquier otro caso, y debajo una
  sección `Detalle ENVIOCC` con los nombres que faltan o sobran.
- Config (`enviocc { }`): `COM_ID`, `DES`, `textoCsv`, `formatoFechaCsv`, `separador`, `columna`, `rutaPdf`, `formatoFechaPdf`, `textoPdf`, `extensionPdf`.
- Estados: `OK`, `DIFERENCIAS`, `CSV_NO_ENCONTRADO`, `RUTA_PDF_NO_ACCESIBLE`, `ERROR_PROCESO`.

### 5.4 Horario y cron

- **Una sola hora para todo el job:** `hora = '15:00'` en la config, interpretada en `zonaHoraria = 'America/Santiago'` (hora de Chile; el
  cron con `TZ=` respeta el horario de verano). Las tareas **no** tienen hora propia.
- `programar_ejecuciones()` (primera línea del template) valida `hora` (`HH:mm`) y registra el cron con `properties([pipelineTriggers([cron(...)])])`.
- **Cada ejecución valida todo** (10 archivos + `SIN_APROBADO` + `ENVIOCC`), sea por cron o lanzada a mano: para probar basta con *Build*.
- Historial: hasta el commit `993df4c` había horas por tarea, `ventanaMinutos`, `franja_horaria()` y el parámetro `HORA`; se quitaron al
  cambiar el requerimiento a una única ejecución diaria.
- `diasAtras` (0 = hoy, 1 = ayer) mueve la fecha de las **tres** tareas; sirve para probar con archivos de días anteriores. Producción: `0`.

### 5.5 Correo único (`enviar_correo_resultados(resultados, sa, ec)`)

1. **Tabla de estatus** `COM_ID | DESCRIPCION | ESTATUS`: las validaciones + la fila de ENVIOCC.
2. **Sección `Validación SIN_APROBADO`**: solo si hay algo que reportar (CSV no encontrado, PDF sin registro o error).
3. **Sección `Detalle ENVIOCC`**: solo si ENVIOCC terminó en error.
- Se envía en cada ejecución (siempre hay validaciones que reportar).
- **Adjuntos:** todos los **CSV encontrados** con su nombre original: los de las validaciones (`r.adjunto`), el de SIN_APROBADO ya limpio
  (`sa.archivoLimpio`) y el de ENVIOCC (`ec.adjunto`). **Ningún PDF.** `emailext` los adjunta con `attachmentsPattern` (lista separada por
  comas, rutas relativas al workspace; por eso el correo se envía antes del `clean_job_workspace()` del `finally`) y el cuerpo los lista
  ("Se adjuntan los archivos encontrados (N)"). Lo que no se encontró no se adjunta. **Nada se archiva en Jenkins** (sin `archiveArtifacts`).
- Cada tarea tiene su propio `try/catch` en el template: si una falla por error técnico, aparece como `ERROR_PROCESO` en el correo y el build queda amarillo, sin perder el resto.

## 5B. Pipeline 3 – `validar-rutas-especificas`

**Objetivo:** una vez al día, a `hora` (en `zonaHoraria = 'America/Santiago'`), buscar el archivo del **día de ejecución** (`diasAtras = 0`)
en cada una de las **4 rutas específicas** y enviar **un correo** con `OK` / `ERROR` por ruta. No hay ruta general, pausa ni adjuntos.

- **Config (`validacion_rutas`):** `host`, `user`, `runAsUser`, `zonaHoraria`, `hora`, `formatoFecha` (`%Y%m%d`), `diasAtras`,
  `estadoSiFaltan`, `archivos` (lista de `[nombre, ruta]`, opcional `proceso`). `nombre` admite comodín `*` y `{fecha}`
  (ej. `testSiniestro_{fecha}.zip`).
- **Ejecución:** `programar_validacion_rutas()` (primera línea del template) valida `hora` y registra el cron diario. Ejecución manual
  (*Build*) = misma validación.
- **Step `validar_rutas()`**: `buscar()` con `ls -1t` (si hay varios, el más reciente); devuelve por archivo
  `[proceso, archivo, ruta, estatus, encontrado, fecha]`. Una ruta no accesible es `ERROR` y el motivo queda en el log.
- **Correo (`enviar_correo_validacion_rutas(resultados)`):** se envía **siempre**. Tabla `PROCESO | RUTA | ESTATUS` (una fila por ruta),
  `OK` verde y `ERROR` rojo; asunto `<asuntoPrefijo> <nombres> <asuntoSufijo> - <fecha>`; destinatarios y CC de su propio
  `notificacion_correo`.
- `hora = '18:30'` y `formatoFecha = '%Y%m%d'` (ej. `20260926`) confirmados. Pendiente: las 4 rutas reales (en la config están de ejemplo, `AJUSTAR`).

## 6. Decisiones tomadas y supuestos

- Las tareas nuevas viven **dentro de la misma librería** (`validacion_horarios`) como bloques anidados, no como librerías aparte, para compartir conexión y rutas.
- Los CSV se procesan en una **copia local**; no se escribe nada en el servidor.
- Se **borran filas**, no columnas, con `Liquidador externo` (el requisito original decía "borrar la columna" y luego "borrar la fila"; se implementó la fila).
- Todo el procesamiento de CSV usa `awk` con separador fijo (`separador` en la config).
- Los dos pipelines comparten la librería `notificacion_correo` (destinatarios, prefijo y sufijo del asunto).

## 7. Puntos a confirmar / mejoras pendientes

1. **`SIN_APROBADO` y filas excluidas:** la comparación usa solo las filas que **quedan** tras borrar `Liquidador externo`. Un PDF cuyo número pertenezca a una fila borrada se reportará como "no encontrado". Decidir si debe compararse contra el CSV completo.
2. **Escribir sobre el CSV original** (hoy solo copia). Requiere permiso de escritura como `usr_datos` en sudoers y conviene guardar un respaldo antes.
3. **CSV complejos:** `awk` no entiende campos entrecomillados que contengan el separador. Si aparece, pasar a Python.
   (El BOM del encabezado sí se tolera: el `gsub(/[^A-Za-z0-9_]/, ...)` lo quita antes de buscar la columna.)
4. **Comparación de números como texto:** `0342432` y `342432` no coinciden (ceros a la izquierda).
5. ~~Con `estadoSiFaltan = 'FAILURE'` no se enviaba correo~~ → resuelto: se usa `currentBuild.result` en vez de `error` (ver §3.6).
6. **`SIN_APROBADO` con 0 PDF** solo se ve en el log; no genera aviso. Y no aparece en la tabla de estatus (ENVIOCC sí); se puede agregar con su propio `COM_ID`.
7. ~~Varios CSV que coincidan~~ → resuelto: SIN_APROBADO y ENVIOCC usan el más reciente por fecha de modificación (`ls -1t`); el log muestra todos.
8. **Nombres en las rutas específicas del pipeline 1:** en algún momento se mencionó `REPORTE{fecha}.csv_*` (números después del `.csv`); la config actual usa `REPORTE_{fecha}_*.csv`. Confirmar el nombre real.
9. ~~Pipeline 1 busca la fecha de "ayer"~~ → cambiado: busca los archivos del **día de ejecución** (`diasAtras = 0`), con horario propio para la ruta general y para cada ruta específica (ver §4).
10. **Duplicación:** `buscar()` y el armado de fechas están repetidos en varios steps. Candidato a refactor (librería o step común) y a unificar los dos steps de correo.
11. **`properties([...])` en el template** reemplaza los triggers/parámetros definidos en la interfaz del job, y se registra la primera vez que el job corre: tras cambiar la hora hay que lanzarlo una vez a mano.
12. ~~Ventana de tolerancia~~ → ya no aplica: con una sola hora, cada ejecución valida todo aunque Jenkins arranque tarde.
13. ~~Ventanas solapadas~~ → ya no aplica (una sola hora, ver §5.4).
14. ~~`buscar()` ocultaba el fallo de `ls` y hacía 2 conexiones SSH~~ → resuelto: una conexión y el `rc` de `ls` decide (ver §3.3).
    Pendiente opcional: distinguir en log/correo "no existe" / "sin permiso" / "falló el SSH" (hoy todo es "ruta no accesible").
15. ~~ENVIOCC con comparación cuadrática~~ → resuelto: `contains` sobre `HashSet`.
16. Menores: `.pdf` fijo en minúscula en SIN_APROBADO; HTML del correo sin escapar; `estadoSiFaltan` sin lista de valores permitidos en
    `library_config`; `log_rotator` declarado pero sin usar; `pipeline_template` de ambos configs pendiente de ajustar a la ruta real.

## 8. Cómo probar

- Commit y push del repo y ejecutar el job una vez a mano (registra el cron).
- Pipeline 2: cualquier ejecución manual (*Build*) corre las tres tareas, sin esperar a las 15:00.
- Para probar con archivos de otro día: cambiar `diasAtras`.
- Si todos los archivos aparecen "no encontrados": probar a mano desde el nodo
  `ssh svc_jenkins@<ip> "sudo -n -u usr_datos ls -1 <ruta>"`. Si dice `a password is required`, falta el sudoers.
- Errores de JTE `field '...' is not used` → falta declarar el campo en `library_config.groovy`. `No such DSL method` → falta la librería en la config.

## 9. Cómo trabajar en este repo (instrucciones para Claude Code)

- Mantén la separación: **config = valores, librería = lógica, template = orquestación**.
- Antes de agregar un campo, añádelo a la config **y** a `library_config.groovy` (incluidos los bloques anidados).
- No pongas valores literales (IP, rutas, nombres de archivo, formatos de fecha) dentro de los steps.
- Conserva el formato de log con ✅ / ❌ y el resumen final; los errores técnicos devuelven `ERROR_PROCESO`, no rompen el pipeline.
- Respeta el shebang `"""#!/bin/sh` en los `sh` con bucles y el escape `\\` / `\$f` dentro de los strings de Groovy.
- Si cambias el contrato de datos (claves de los mapas que devuelven los steps), actualiza también el step de correo y este documento.
- No hay Jenkins disponible en el entorno de desarrollo para ejecutar los pipelines: revisa la sintaxis con cuidado y explica cómo probar cada cambio.
