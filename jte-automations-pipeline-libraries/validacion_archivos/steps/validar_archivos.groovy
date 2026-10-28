// Valida lo que toca en este horario:
//  - horaRutaGeneral → la ruta general de TODOS los archivos (sin pausa)
//  - horaEspecifica  → la ruta específica de los archivos de ese horario (estado PAUSADO = no se busca, sale PAUSADO)
// En la ruta general, nombre admite {fecha}, {mes} y comodín * (si hay varios, se toma el más reciente); con modificadoMismoDia = true además se exige que el archivo
// se haya modificado el día de ejecución (fecha de modificación del servidor, en zonaHoraria).
// modo: vacío = según la hora actual (cron); TODOS = todo (pruebas); HH:mm = simula ese horario (pruebas).
// Devuelve [fecha, horario, descripcion, resultados]; resultados por archivo: [proceso, archivo, estatus, rutas, fecha].
Map call(String modo = '') {
    def host        = config.host
    def user        = config.user
    def runAsUser   = config.runAsUser
    def faltantes   = []
    def resultados  = []
    def totalChecks = 0
    def pausados    = 0

    // Fecha de los archivos (día de ejecución con diasAtras = 0), mes, día para comparar la modificación y hora actual,
    // en la zona horaria de la config
    def datos = sh(
        returnStdout: true,
        script: """#!/bin/sh
export TZ='${config.zonaHoraria}'
date -d '${config.diasAtras} days ago' +'${config.formatoFecha}'
date -d '${config.diasAtras} days ago' +'${config.formatoMes}'
date -d '${config.diasAtras} days ago' +'%Y%m%d'
date +'%H:%M'
"""
    ).trim().readLines()

    def fecha      = datos[0]
    def mes        = datos[1]
    def diaEjec    = datos[2]
    def horaActual = datos[3]

    // ---------- ¿Qué horario es esta ejecución? ----------
    def m          = modo?.trim()
    def todos      = (m?.toUpperCase() == 'TODOS')
    def referencia = (m && !todos) ? m : horaActual

    def aMinutos = { h ->
        def p = String.valueOf(h).trim().split(':')
        return p[0].toInteger() * 60 + p[1].toInteger()
    }

    // El horario es la última hora programada que ya pasó (si Jenkins arranca un minuto tarde, 11:16 sigue siendo 11:15)
    def horas = [config.horaRutaGeneral]
    config.archivos.each { item -> horas << item.horaEspecifica }
    horas = horas.collect { String.valueOf(it).trim() }.unique()

    def franja = null
    if (!todos) {
        def ref        = aMinutos(referencia)
        def candidatas = horas.findAll { h ->
            def diff = ref - aMinutos(h)
            return (diff >= 0)
        }
        if (candidatas) {
            def max = candidatas.collect { aMinutos(it) }.max()
            franja  = candidatas.find { aMinutos(it) == max }
        }
    }

    def validarGeneral = todos || (franja != null && aMinutos(config.horaRutaGeneral) == aMinutos(franja))
    def esEspecifico   = { item -> todos || (franja != null && aMinutos(item.horaEspecifica) == aMinutos(franja)) }
    def especificos    = config.archivos.findAll { esEspecifico(it) }

    def partes = []
    if (validarGeneral) { partes << 'ruta general' }
    if (especificos)    { partes << 'rutas específicas' }
    def horario     = todos ? 'TODOS' : franja
    def descripcion = partes.join(' y ')

    echo "Fecha: ${fecha} | Hora: ${horaActual} | Referencia: ${todos ? 'TODOS' : referencia} | Horario: ${horario ?: 'ninguno'}" +
         (descripcion ? " → ${descripcion}" : '')

    if (!validarGeneral && !especificos) {
        echo "No hay validaciones programadas antes de las ${referencia} (horarios: ${horas.join(', ')})"
        return [fecha: fecha, horario: null, descripcion: '', resultados: []]
    }

    // Busca en una ruta los archivos que coinciden con el patrón (el más reciente primero, ls -1t).
    // Devuelve null si la ruta no es accesible, o la lista de coincidencias (vacía si no hay).
    def buscar = { String ruta, String patron ->
        // Una sola conexión: lista la carpeta en un archivo local; el rc indica si la ruta es accesible
        def rc = sh(
            returnStatus: true,
            script: """#!/bin/sh
ssh -o StrictHostKeyChecking=no ${user}@${host} \\
    "sudo -n -u ${runAsUser} test -d '${ruta}' && sudo -n -u ${runAsUser} ls -1t '${ruta}'" > buscar_listado.txt
"""
        )
        if (rc != 0) {
            return null     // no existe, sin permiso, sudo pidió contraseña o falló el SSH
        }

        // Filtrar en el nodo, sin volver a conectarse
        def salida = sh(
            returnStdout: true,
            script: """#!/bin/sh
while IFS= read -r f; do
    case "\$f" in
        ${patron}) echo "\$f" ;;
    esac
done < buscar_listado.txt
"""
        ).trim()

        return salida ? salida.readLines() : []
    }

    // Día de modificación (AAAAMMDD en zonaHoraria) de un archivo del servidor; '' si no se pudo leer.
    // ls entrega la fecha en segundos (epoch) para no depender de la zona horaria del servidor.
    def diaModificacion = { String ruta, String archivo ->
        return sh(
            returnStdout: true,
            script: """#!/bin/sh
epoch=\$(ssh -o StrictHostKeyChecking=no ${user}@${host} \\
    "sudo -n -u ${runAsUser} ls -l --time-style=+%s '${ruta}/${archivo}'" | awk '{print \$6}')
if [ -n "\$epoch" ]; then
    TZ='${config.zonaHoraria}' date -d "@\$epoch" +'%Y%m%d'
fi
"""
        ).trim()
    }

    config.archivos.each { item ->
        def especifico = esEspecifico(item)
        if (!validarGeneral && !especifico) {
            return      // este archivo no tiene nada que validar en este horario
        }

        def nombre  = item.nombre.replace('{fecha}', fecha).replace('{mes}', mes)
        def patron  = item.patronEspecifico.replace('{fecha}', fecha)
        def rutaEsp = item.rutaEspecifica
        def rutas   = []

        // Nombre corto: sin {fecha}, {mes}, * ni .csv (y sin "_" sobrante al final)
        def nombreCorto = item.nombre.replace('{fecha}', '').replace('{mes}', '').replace('*', '').replace('.csv', '')
        if (nombreCorto.endsWith('_')) {
            nombreCorto = nombreCorto.substring(0, nombreCorto.length() - 1)
        }

        echo "------ ${nombre} ------"

        // 1) Ruta general (nombre exacto o con comodín): siempre se valida en su horario, sin pausa
        if (validarGeneral) {
            totalChecks++
            def okGeneral = false
            def enGeneral = buscar(config.rutaGeneral, nombre)
            if (enGeneral == null) {
                echo "❌ [GENERAL] Ruta no accesible: ${config.rutaGeneral}"
                faltantes << "[GENERAL] ${config.rutaGeneral}/${nombre} (ruta no accesible)"
            } else if (enGeneral && item.modificadoMismoDia) {
                // Además de existir, debe haberse modificado el día de ejecución
                def diaMod = diaModificacion(config.rutaGeneral, enGeneral[0])
                if (diaMod == diaEjec) {
                    echo "✅ [GENERAL] ${config.rutaGeneral}/${enGeneral[0]} (modificado el ${diaMod})"
                    okGeneral = true
                } else {
                    def motivo = diaMod ? "modificado el ${diaMod}, se esperaba ${diaEjec}" : 'no se pudo leer la fecha de modificación'
                    echo "❌ [GENERAL] ${config.rutaGeneral}/${enGeneral[0]} existe pero ${motivo}"
                    faltantes << "[GENERAL] ${config.rutaGeneral}/${nombre} (${motivo})"
                }
            } else if (enGeneral) {
                echo "✅ [GENERAL] ${config.rutaGeneral}/${enGeneral[0]}"
                okGeneral = true
            } else {
                echo "❌ [GENERAL] No encontrado: ${config.rutaGeneral}/${nombre}"
                faltantes << "[GENERAL] ${config.rutaGeneral}/${nombre}"
            }
            rutas << [ruta: config.rutaGeneral, estatus: okGeneral ? 'OK' : 'ERROR']
        }

        // 2) Ruta específica (patrón con comodín) en su horario; PAUSADO = no se busca
        if (especifico) {
            def estado = String.valueOf(item.estado ?: 'ACTIVO').trim().toUpperCase()
            if (estado == 'PAUSADO') {
                pausados++
                echo "⏸️ [ESPECÍFICA] Monitoreo pausado, no se busca: ${rutaEsp}"
                rutas << [ruta: rutaEsp, estatus: 'PAUSADO']
            } else {
                totalChecks++
                def okEspecifica = false
                def enEspecifica = buscar(rutaEsp, patron)
                if (enEspecifica == null) {
                    echo "❌ [ESPECÍFICA] Ruta no accesible: ${rutaEsp}"
                    faltantes << "[ESPECÍFICA] ${rutaEsp}/${patron} (ruta no accesible)"
                } else if (enEspecifica) {
                    echo "✅ [ESPECÍFICA] ${rutaEsp}/${enEspecifica.join(', ')}"
                    okEspecifica = true
                } else {
                    echo "❌ [ESPECÍFICA] No encontrado: ${rutaEsp}/${patron}"
                    faltantes << "[ESPECÍFICA] ${rutaEsp}/${patron}"
                }
                rutas << [ruta: rutaEsp, estatus: okEspecifica ? 'OK' : 'ERROR']
            }
        }

        // Estatus del archivo: ERROR si alguna ruta falló; PAUSADO si todo lo validado está pausado; si no, OK
        def conError  = rutas.findAll { it.estatus == 'ERROR' }.size()
        def enPausa   = rutas.findAll { it.estatus == 'PAUSADO' }.size()
        resultados << [
            proceso: item.proceso ?: nombreCorto,
            archivo: nombreCorto,
            estatus: conError ? 'ERROR' : (enPausa == rutas.size() ? 'PAUSADO' : 'OK'),
            rutas:   rutas,
            fecha:   fecha
        ]
    }

    // Resumen
    echo "=========== RESUMEN (${horario}: ${descripcion}) ==========="
    echo "Validaciones OK: ${totalChecks - faltantes.size()} de ${totalChecks}"
    echo "Faltantes:       ${faltantes.size()} de ${totalChecks}"
    if (pausados) {
        echo "Pausados:        ${pausados} (no se buscaron)"
    }

    if (faltantes) {
        echo "No encontrados:\n - ${faltantes.join('\n - ')}"

        // No se usa error(): el pipeline debe seguir hasta enviar el correo
        switch (config.estadoSiFaltan) {
            case 'FAILURE':
                echo "Faltan ${faltantes.size()} validación(es): el build se marca como FAILURE"
                currentBuild.result = 'FAILURE'
                break
            case 'UNSTABLE':
                unstable("Faltan ${faltantes.size()} validación(es)")
                break
            default:
                break
        }
    } else {
        echo "Todas las validaciones fueron exitosas"
    }

    return [fecha: fecha, horario: horario, descripcion: descripcion, resultados: resultados]
}
