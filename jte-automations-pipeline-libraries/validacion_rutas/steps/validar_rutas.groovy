// Busca el archivo del día en cada ruta de config.archivos.
// Devuelve una lista; por archivo: [proceso, archivo, ruta, estatus, encontrado, fecha].
List call() {
    def host       = config.host
    def user       = config.user
    def runAsUser  = config.runAsUser
    def faltantes  = []
    def resultados = []

    // Fecha de los archivos (día de ejecución con diasAtras = 0) en la zona horaria de la config
    def fecha = sh(
        returnStdout: true,
        script: """#!/bin/sh
TZ='${config.zonaHoraria}' date -d '${config.diasAtras} days ago' +'${config.formatoFecha}'
"""
    ).trim()

    echo "Fecha de los archivos: ${fecha} | Rutas a validar: ${config.archivos.size()}"

    // Busca en una ruta los archivos que coinciden con el patrón.
    // Devuelve null si la ruta no es accesible, o la lista de coincidencias (vacía si no hay).
    def buscar = { String ruta, String patron ->
        // Una sola conexión: lista la carpeta en un archivo local; el rc indica si la ruta es accesible.
        // ls -1t ordena del más reciente al más antiguo (fecha de modificación).
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

    config.archivos.each { item ->
        def nombre = item.nombre.replace('{fecha}', fecha)
        def ruta   = item.ruta

        // Nombre corto: sin {fecha}, extensión, comodines ni "_" sobrantes (testSiniestro_{fecha}.zip → testSiniestro)
        def nombreCorto = item.nombre.replace('{fecha}', '').replaceAll(/\.[^.]+$/, '').replace('*', '')
        while (nombreCorto.startsWith('_')) {
            nombreCorto = nombreCorto.substring(1)
        }
        while (nombreCorto.endsWith('_')) {
            nombreCorto = nombreCorto.substring(0, nombreCorto.length() - 1)
        }

        def encontrados = buscar(ruta, nombre)
        def ok          = false

        if (encontrados == null) {
            echo "❌ Ruta no accesible: ${ruta}"
            faltantes << "${ruta}/${nombre} (ruta no accesible)"
        } else if (encontrados) {
            echo "✅ ${ruta}/${encontrados[0]}"
            ok = true
        } else {
            echo "❌ No encontrado: ${ruta}/${nombre}"
            faltantes << "${ruta}/${nombre}"
        }

        resultados << [
            proceso:    item.proceso ?: nombreCorto,
            archivo:    nombreCorto,
            ruta:       ruta,
            estatus:    ok ? 'OK' : 'ERROR',
            encontrado: ok ? encontrados[0] : null,
            fecha:      fecha
        ]
    }

    // Resumen
    echo "=========== RESUMEN ==========="
    echo "OK:        ${resultados.size() - faltantes.size()} de ${resultados.size()}"
    echo "Faltantes: ${faltantes.size()} de ${resultados.size()}"

    if (faltantes) {
        echo "No encontrados:\n - ${faltantes.join('\n - ')}"

        // No se usa error(): el pipeline debe seguir hasta enviar el correo
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
