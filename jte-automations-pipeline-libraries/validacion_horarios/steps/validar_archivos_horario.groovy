List call() {
    def host      = config.host
    def user      = config.user
    def runAsUser = config.runAsUser
    def faltantes = []
    def resultados = []

    // Fecha y mes en la zona horaria configurada
    def datos = sh(
        returnStdout: true,
        script: """#!/bin/sh
export TZ='${config.zonaHoraria}'
date -d '${config.diasAtras} days ago' +'${config.formatoFecha}'
date -d '${config.diasAtras} days ago' +'${config.formatoMes}'
"""
    ).trim().readLines()

    def fecha = datos[0]
    def mes   = datos[1]

    def seleccion = config.archivos

    echo "Fecha: ${fecha} | Mes: ${mes} | Validaciones a ejecutar: ${seleccion.size()}"

    // Busca en una ruta los archivos que coinciden con el patrón.
    // Devuelve null si la ruta no es accesible, o la lista de coincidencias (vacía si no hay).
    def buscar = { String ruta, String patron ->
        // Una sola conexión: lista la carpeta en un archivo local; el rc indica si la ruta es accesible.
        // ls -1t ordena del más reciente al más antiguo (fecha de modificación): la primera coincidencia es la última creada.
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
        def ok      = false
        def adjunto = null

        if (encontrados == null) {
            echo "❌ [COM_ID ${item.COM_ID ?: '-'}] Ruta no accesible: ${ruta}"
            faltantes << "[COM_ID ${item.COM_ID ?: '-'}] ${ruta}/${nombre} (ruta no accesible)"
        } else if (encontrados) {
            def archivo = encontrados[0]
            echo "✅ [COM_ID ${item.COM_ID ?: '-'}] ${ruta}/${archivo}"
            if (encontrados.size() > 1) {
                echo "   ⚠️ Hay ${encontrados.size()} que coinciden, se usa el más reciente: ${encontrados.join(', ')}"
            }
            ok = true

            // Copia al workspace con su nombre original para adjuntarla al correo (no se archiva en Jenkins)
            def rcCopia = sh(
                returnStatus: true,
                script: """#!/bin/sh
ssh -o StrictHostKeyChecking=no ${user}@${host} "sudo -n -u ${runAsUser} cat '${ruta}/${archivo}'" > '${archivo}'
"""
            )
            if (rcCopia == 0) {
                adjunto = archivo
            } else {
                echo "   ⚠️ No se pudo copiar ${archivo} (rc=${rcCopia}): no se adjunta al correo"
            }
        } else {
            echo "❌ [COM_ID ${item.COM_ID ?: '-'}] No encontrado: ${ruta}/${nombre}"
            faltantes << "[COM_ID ${item.COM_ID ?: '-'}] ${ruta}/${nombre}"
        }

        resultados << [
            comId:       item.COM_ID,
            descripcion: item.DES ?: nombreCorto,
            proceso:     item.proceso ?: nombreCorto,
            archivo:     nombreCorto,
            estatus:     ok ? 'OK' : 'ERROR',
            adjunto:     adjunto,       // archivo copiado al workspace (null si no se encontró o no se pudo copiar)
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
