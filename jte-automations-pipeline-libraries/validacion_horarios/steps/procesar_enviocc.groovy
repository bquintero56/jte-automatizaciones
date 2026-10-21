Map call() {
    def c         = config.enviocc
    def host      = config.host
    def user      = config.user
    def runAsUser = config.runAsUser
    def titulo    = c.textoCsv

    // Datos que van en todos los resultados (para la fila de la tabla del correo)
    def base = [titulo: titulo, comId: c.COM_ID, descripcion: c.DES]

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

    def rutaCsv   = config.rutaBase.replace('{mes}', mes)
    def patronCsv = "*_${c.textoCsv}_${fechaCsv}.csv"
    // prefijoPdf: '' = los PDF empiezan con textoPdf (CCTEST_..._21_10_2026.pdf); '*_' = empiezan con números + "_"
    def patronPdf = "${c.prefijoPdf}${c.textoPdf}_*_${fechaPdf}${c.extensionPdf}"

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

    // Quita los números iniciales y la extensión: 2131_CCTEST_123_09_10_2026.pdf -> CCTEST_123_09_10_2026
    def normalizar = { String nombre ->
        def x = nombre.trim()
        def i = x.indexOf("_${c.textoPdf}_")
        if (i >= 0) {
            x = x.substring(i + 1)
        }
        def ext = c.extensionPdf
        if (ext && x.toLowerCase().endsWith(ext.toLowerCase())) {
            x = x.substring(0, x.length() - ext.length())
        }
        return x
    }

    // ---------- 1) ¿Existe el CSV? ----------
    echo "Buscando CSV: ${rutaCsv}/${patronCsv}"
    def csvs = buscar(rutaCsv, patronCsv)

    if (!csvs) {
        echo "❌ CSV no encontrado${csvs == null ? ' (ruta no accesible)' : ''}: ${rutaCsv}/${patronCsv}"
        echo "Se detiene el proceso de ${titulo} (el pipeline no falla)"
        return base + [
            estado:          'CSV_NO_ENCONTRADO',
            fecha:           fechaCsv,
            ruta:            rutaCsv,
            patronCsv:       patronCsv,
            rutaInaccesible: (csvs == null)
        ]
    }

    def nombreCsv = csvs[0]
    echo "✅ CSV encontrado: ${rutaCsv}/${nombreCsv}"
    if (csvs.size() > 1) {
        echo "⚠️ Hay ${csvs.size()} CSV que coinciden (del más reciente al más antiguo): ${csvs.join(', ')}"
        echo "   Se usa el más reciente: ${nombreCsv}"
    }

    // ---------- 2) Copia local del CSV (con su nombre original: se adjunta al correo, no se archiva en Jenkins) ----------
    def rcCopia = sh(
        returnStatus: true,
        script: """#!/bin/sh
ssh -o StrictHostKeyChecking=no ${user}@${host} "sudo -n -u ${runAsUser} cat '${rutaCsv}/${nombreCsv}'" > '${nombreCsv}'
"""
    )
    if (rcCopia != 0) {
        echo "❌ No se pudo leer el CSV ${rutaCsv}/${nombreCsv} (rc=${rcCopia})"
        return base + [
            estado:  'ERROR_PROCESO',
            fecha:   fechaCsv,
            mensaje: "No se pudo leer el CSV ${rutaCsv}/${nombreCsv} (rc=${rcCopia})"
        ]
    }

    // Desde aquí todos los resultados llevan el CSV para adjuntarlo
    base = base + [adjunto: nombreCsv]

    // ---------- 3) Extraer la columna ----------
    def awk = '''
BEGIN { FS = sep }
{ sub(/[[:space:]]+$/, "") }
NR == 1 {
    for (i = 1; i <= NF; i++) {
        h = $i
        gsub(/[^A-Za-z0-9_]/, "", h)
        if (h == col) c = i
    }
    if (!c) {
        print "No se encontró la columna " col " en el encabezado" > "/dev/stderr"
        exit 3
    }
    next
}
{
    v = $c
    gsub(/^[[:space:]"]+|[[:space:]"]+$/, "", v)
    if (v != "") print v
}
'''
    writeFile file: 'enviocc_extraer.awk', text: awk

    def rcAwk = sh(
        returnStatus: true,
        script: """#!/bin/sh
awk -v sep='${c.separador}' -v col='${c.columna}' -f enviocc_extraer.awk '${nombreCsv}' > enviocc_nombres.txt
"""
    )
    if (rcAwk != 0) {
        def msg = "No se pudo leer la columna ${c.columna}: revisa que exista en el encabezado y que el separador sea '${c.separador}'"
        echo "❌ ${msg}"
        return base + [
            estado:  'ERROR_PROCESO',
            fecha:   fechaCsv,
            mensaje: msg
        ]
    }

    def enArchivo = readFile('enviocc_nombres.txt').readLines()
        .collect { normalizar(it) }.findAll { it }.unique()

    echo "Registros en la columna ${c.columna}: ${enArchivo.size()}"

    // ---------- 4) PDFs de la ruta ----------
    echo "Buscando PDFs: ${c.rutaPdf}/${patronPdf}"
    def pdfs = buscar(c.rutaPdf, patronPdf)

    if (pdfs == null) {
        echo "❌ Ruta de PDFs no accesible: ${c.rutaPdf}"
        return base + [
            estado:    'RUTA_PDF_NO_ACCESIBLE',
            fecha:     fechaCsv,
            nombreCsv: nombreCsv,
            rutaPdf:   c.rutaPdf
        ]
    }

    def enRuta = pdfs.collect { normalizar(it) }.findAll { it }.unique()
    echo "PDFs encontrados en la ruta: ${enRuta.size()}"

    // ---------- 5) Comparar ----------
    // Búsqueda en Set (instantánea) en vez de en List (recorre toda la lista por cada nombre)
    def setArchivo = new HashSet(enArchivo)
    def setRuta    = new HashSet(enRuta)
    def faltanPdf  = enArchivo.findAll { !setRuta.contains(it) }     // están en el archivo, no hay PDF
    def sobranPdf  = enRuta.findAll    { !setArchivo.contains(it) }  // hay PDF, no están en el archivo

    if (faltanPdf) {
        echo "❌ FALTAN PDF en la ruta (generar ticket): ${faltanPdf.size()}"
        faltanPdf.each { echo "   - ${it}" }
    }
    if (sobranPdf) {
        echo "❌ SOBRAN PDF en la ruta, no están en el archivo (generar ticket solicitando CSV): ${sobranPdf.size()}"
        sobranPdf.each { echo "   - ${it}" }
    }
    if (!faltanPdf && !sobranPdf) {
        echo "✅ Los nombres del archivo y los PDF de la ruta coinciden"
    }

    return base + [
        estado:       (faltanPdf || sobranPdf) ? 'DIFERENCIAS' : 'OK',
        fecha:        fechaCsv,
        nombreCsv:    nombreCsv,
        columna:      c.columna,
        rutaPdf:      c.rutaPdf,
        totalArchivo: enArchivo.size(),
        totalRuta:    enRuta.size(),
        faltanPdf:    faltanPdf,
        sobranPdf:    sobranPdf
    ]
}
