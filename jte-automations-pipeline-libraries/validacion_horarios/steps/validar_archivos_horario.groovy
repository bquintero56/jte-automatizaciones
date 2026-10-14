List call(String modo = '') {
    def host      = config.host
    def user      = config.user
    def runAsUser = config.runAsUser
    def faltantes = []
    def resultados = []

    // Fecha, mes y hora actual en la zona horaria configurada
    def datos = sh(
        returnStdout: true,
        script: """#!/bin/sh
export TZ='${config.zonaHoraria}'
date -d '${config.diasAtras} days ago' +'${config.formatoFecha}'
date -d '${config.diasAtras} days ago' +'${config.formatoMes}'
date +'%H:%M'
"""
    ).trim().readLines()

    def fecha      = datos[0]
    def mes        = datos[1]
    def horaActual = datos[2]

    // Qué archivos tocan en esta ejecución
    def todos      = (modo?.trim()?.toUpperCase() == 'TODOS')
    def referencia = todos ? horaActual : (modo?.trim() ?: horaActual)

    def aMinutos = { String hhmm ->
        def p = hhmm.split(':')
        return p[0].toInteger() * 60 + p[1].toInteger()
    }

    def seleccion = config.archivos.findAll { item ->
        if (todos) {
            return true
        }
        def diff = aMinutos(referencia) - aMinutos(item.hora)
        return (diff >= 0 && diff < config.ventanaMinutos)
    }

    echo "Fecha: ${fecha} | Mes: ${mes} | Referencia: ${todos ? 'TODOS' : referencia}"

    if (!seleccion) {
        echo "No hay validaciones programadas para las ${referencia} (ventana de ${config.ventanaMinutos} min)"
        return []
    }

    echo "Validaciones a ejecutar: ${seleccion.size()}"

    // Busca en una ruta los archivos que coinciden con el patrón.
    // Devuelve null si la ruta no es accesible, o la lista de coincidencias (vacía si no hay).
    def buscar = { String ruta, String patron ->
        // Una sola conexión: lista la carpeta en un archivo local; el rc indica si la ruta es accesible
        def rc = sh(
            returnStatus: true,
            script: """#!/bin/sh
ssh -o StrictHostKeyChecking=no ${user}@${host} \\
    "sudo -n -u ${runAsUser} test -d '${ruta}' && sudo -n -u ${runAsUser} ls -1 '${ruta}'" > buscar_listado.txt
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

    seleccion.each { item ->
        def nombre = (config.prefijoNombre + item.nombre).replace('{fecha}', fecha)
        def ruta   = (item.ruta ?: config.rutaBase).replace('{mes}', mes)

        // Nombre corto: sin {fecha}, .csv, comodines ni "_" sobrantes
        def nombreCorto = item.nombre.replace('{fecha}', '').replace('.csv', '').replace('*', '')
        while (nombreCorto.startsWith('_')) {
            nombreCorto = nombreCorto.substring(1)
        }
        while (nombreCorto.endsWith('_')) {
            nombreCorto = nombreCorto.substring(0, nombreCorto.length() - 1)
        }

        def encontrados = buscar(ruta, nombre)
        def ok = false

        if (encontrados == null) {
            echo "❌ [${item.hora}] Ruta no accesible: ${ruta}"
            faltantes << "[${item.hora}] ${ruta}/${nombre} (ruta no accesible)"
        } else if (encontrados) {
            echo "✅ [${item.hora}] ${ruta}/${encontrados[0]}"
            ok = true
        } else {
            echo "❌ [${item.hora}] No encontrado: ${ruta}/${nombre}"
            faltantes << "[${item.hora}] ${ruta}/${nombre}"
        }

        resultados << [
            comId:       item.COM_ID,
            descripcion: item.DES ?: nombreCorto,
            proceso:     item.proceso ?: nombreCorto,
            archivo:     nombreCorto,
            estatus:     ok ? 'OK' : 'ERROR',
            fecha:       fecha
        ]
    }

    echo "=========== RESUMEN ==========="
    echo "OK:        ${resultados.size() - faltantes.size()} de ${resultados.size()}"
    echo "Faltantes: ${faltantes.size()} de ${resultados.size()}"

    if (faltantes) {
        echo "No encontrados:\n - ${faltantes.join('\n - ')}"

        // No se usa error(): el pipeline debe seguir con SIN_APROBADO, ENVIOCC y el correo
        switch (config.estadoSiFaltan) {
            case 'FAILURE':
                echo "Faltan ${faltantes.size()} archivo(s): el build se marca como FAILURE"
                currentBuild.result = 'FAILURE'
                break
            case 'UNSTABLE':
                unstable("Faltan ${faltantes.size()} archivo(s)")
                break
            default:
                break
        }
    } else {
        echo "Todos los archivos fueron encontrados"
    }

    return resultados
}
