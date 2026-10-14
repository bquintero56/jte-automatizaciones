// franja: resultado de franja_horaria() → [todos, referencia, horas]
Map call(Map franja) {
    def c         = config.sinAprobado
    def host      = config.host
    def user      = config.user
    def runAsUser = config.runAsUser

    // ¿Le toca correr en esta franja?
    if (!franja.todos && !franja.horas.contains(c.hora)) {
        echo "SIN_APROBADO está programado a las ${c.hora}; referencia ${franja.referencia}, se omite"
        return [estado: 'OMITIDO']
    }

    // Fechas y mes (respetan diasAtras)
    def datos = sh(
        returnStdout: true,
        script: """#!/bin/sh
export TZ='${config.zonaHoraria}'
date -d '${config.diasAtras} days ago' +'${c.formatoFechaCsv}'
date -d '${config.diasAtras} days ago' +'${c.formatoFechaPdf}'
date -d '${config.diasAtras} days ago' +'${config.formatoMes}'
"""
    ).trim().readLines()

    def fechaCsv = datos[0]
    def fechaPdf = datos[1]
    def mes      = datos[2]

    // Título legible: textoFijo sin "_" en los extremos
    def titulo = c.textoFijo
    while (titulo.startsWith('_')) { titulo = titulo.substring(1) }
    while (titulo.endsWith('_'))   { titulo = titulo.substring(0, titulo.length() - 1) }

    // CSV y PDFs están en la misma ruta
    def ruta      = config.rutaBase.replace('{mes}', mes)
    def patronCsv = "*${c.textoFijo}${fechaCsv}.csv"
    def patronPdf = "*${c.textoFijo}*_${fechaPdf}.pdf"

    // Busca en una ruta los archivos que coinciden con el patrón.
    // Devuelve null si la ruta no es accesible, o la lista de coincidencias (vacía si no hay).
    def buscar = { String rutaBuscar, String patron ->
        // Una sola conexión: lista la carpeta en un archivo local; el rc indica si la ruta es accesible
        def rc = sh(
            returnStatus: true,
            script: """#!/bin/sh
ssh -o StrictHostKeyChecking=no ${user}@${host} \\
    "sudo -n -u ${runAsUser} test -d '${rutaBuscar}' && sudo -n -u ${runAsUser} ls -1 '${rutaBuscar}'" > buscar_listado.txt
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

    // ---------- 1) ¿Existe el CSV? ----------
    echo "Buscando CSV: ${ruta}/${patronCsv}"
    def csvs = buscar(ruta, patronCsv)

    if (!csvs) {
        echo "❌ CSV no encontrado${csvs == null ? ' (ruta no accesible)' : ''}: ${ruta}/${patronCsv}"
        echo "Se detiene el proceso de ${titulo} (el pipeline no falla)"
        return [
            estado:          'CSV_NO_ENCONTRADO',
            titulo:          titulo,
            fecha:           fechaCsv,
            ruta:            ruta,
            patronCsv:       patronCsv,
            rutaInaccesible: (csvs == null)
        ]
    }

    def nombreCsv = csvs[0]
    echo "✅ CSV encontrado: ${ruta}/${nombreCsv}"
    if (csvs.size() > 1) {
        echo "⚠️ Hay ${csvs.size()} CSV que coinciden, se usa el primero: ${csvs.join(', ')}"
    }

    // ---------- 2) Copia local del CSV ----------
    def rcCopia = sh(
        returnStatus: true,
        script: """#!/bin/sh
ssh -o StrictHostKeyChecking=no ${user}@${host} "sudo -n -u ${runAsUser} cat '${ruta}/${nombreCsv}'" > sin_aprobado_original.csv
"""
    )
    if (rcCopia != 0) {
        error "No se pudo leer el CSV ${ruta}/${nombreCsv} (rc=${rcCopia})"
    }

    // ---------- 3) Borrar filas con "Liquidador externo" (en la copia) ----------
    def awk = '''
BEGIN { FS = sep; OFS = sep; removed = 0; kept = 0 }
{ sub(/[[:space:]]+$/, "") }
NR == 1 {
    for (i = 1; i <= NF; i++) {
        h = $i
        gsub(/[^A-Za-z0-9_]/, "", h)
        if (h == colTipo) t = i
        if (h == colSin)  n = i
    }
    if (!t || !n) {
        print "No se encontraron las columnas " colTipo " / " colSin " en el encabezado" > "/dev/stderr"
        exit 3
    }
    print
    next
}
{
    v = $t
    gsub(/^[[:space:]"]+|[[:space:]"]+$/, "", v)
    if (tolower(v) == tolower(excluir)) { removed++; next }
    s = $n
    gsub(/^[[:space:]"]+|[[:space:]"]+$/, "", s)
    print s > "sin_aprobado_siniestros.txt"
    kept++
    print
}
END { printf "%d %d\\n", removed, kept > "sin_aprobado_stats.txt" }
'''
    writeFile file: 'sin_aprobado_filtrar.awk', text: awk

    def rcAwk = sh(
        returnStatus: true,
        script: """#!/bin/sh
: > sin_aprobado_siniestros.txt
awk -v sep='${c.separador}' -v colTipo='${c.columnaTipo}' -v colSin='${c.columnaSiniestro}' -v excluir='${c.valorExcluir}' -f sin_aprobado_filtrar.awk sin_aprobado_original.csv > sin_aprobado_limpio.csv
"""
    )
    if (rcAwk != 0) {
        error "No se pudo procesar el CSV: revisa que existan las columnas ${c.columnaTipo} y ${c.columnaSiniestro} y que el separador sea '${c.separador}'"
    }

    def stats         = readFile('sin_aprobado_stats.txt').trim().split(' ')
    def eliminadas    = stats[0].toInteger()
    def conservadas   = stats[1].toInteger()
    def siniestrosCsv = readFile('sin_aprobado_siniestros.txt').readLines().collect { it.trim() }.findAll { it }

    echo "Filas con '${c.valorExcluir}' eliminadas: ${eliminadas} | filas restantes: ${conservadas}"
    archiveArtifacts artifacts: 'sin_aprobado_limpio.csv', allowEmptyArchive: true

    // ---------- 4) PDFs ----------
    echo "Buscando PDFs: ${ruta}/${patronPdf}"
    def pdfs = buscar(ruta, patronPdf)
    def rutaPdfInaccesible = (pdfs == null)
    if (rutaPdfInaccesible) {
        echo "❌ Ruta de PDFs no accesible: ${ruta}"
        pdfs = []
    }

    echo "PDFs encontrados: ${pdfs.size()}"

    // Número del medio: entre textoFijo y _<fecha>.pdf
    def sufijo  = "_${fechaPdf}.pdf"
    def detalle = []
    pdfs.each { p ->
        def inicio = p.indexOf(c.textoFijo) + c.textoFijo.length()
        def medio  = p.substring(inicio, p.length() - sufijo.length())
        if (medio) {
            detalle << [numero: medio, pdf: p]
            echo "   📄 ${p}  →  ${medio}"
        }
    }

    // ---------- 5) Comparar con NUMERO_SINIESTRO ----------
    def faltantes = detalle.findAll { !siniestrosCsv.contains(it.numero) }

    if (faltantes) {
        echo "❌ Números de PDF que NO están en la columna ${c.columnaSiniestro}:"
        faltantes.each { echo "   - ${it.numero}  (${it.pdf})" }
    } else {
        echo "✅ Todos los números de los PDFs están en la columna ${c.columnaSiniestro}"
    }

    return [
        estado:             faltantes ? 'PDF_SIN_REGISTRO' : 'OK',
        titulo:             titulo,
        fecha:              fechaCsv,
        nombreCsv:          nombreCsv,
        columnaSiniestro:   c.columnaSiniestro,
        valorExcluir:       c.valorExcluir,
        eliminadas:         eliminadas,
        totalPdf:           pdfs.size(),
        rutaPdfInaccesible: rutaPdfInaccesible,
        rutaPdf:            ruta,
        faltantes:          faltantes
    ]
}
