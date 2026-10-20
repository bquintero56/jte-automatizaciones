void call(List resultados, Map sa = [:], Map ec = [:]) {
    def haySA = (sa.estado == 'CSV_NO_ENCONTRADO' || sa.estado == 'PDF_SIN_REGISTRO' || sa.estado == 'ERROR_PROCESO')

    // ENVIOCC corrió en esta ejecución (aparece en la tabla) / tiene algo que detallar
    def ecEjecuto = (ec.estado as boolean)
    def hayEC = (ec.estado == 'CSV_NO_ENCONTRADO' || ec.estado == 'RUTA_PDF_NO_ACCESIBLE' ||
                 ec.estado == 'DIFERENCIAS' || ec.estado == 'ERROR_PROCESO')

    def listaNombres = resultados.collect { it.archivo }
    if (ecEjecuto) {
        listaNombres << (ec.titulo ?: 'ENVIOCC')
    }
    def nombres = listaNombres ? listaNombres.join(', ') : (haySA ? sa.titulo : '')
    def fecha   = resultados ? resultados[0].fecha : (ec.fecha ?: (sa.fecha ?: ''))

    def estilo = 'border:1px solid #999;padding:8px 12px;'

    // Tabla simple de una columna
    def tabla = { String encabezado, List items ->
        def filas = items.collect { n ->
            """
            <tr>
                <td style="${estilo}">${n}</td>
            </tr>"""
        }.join('')

        return """
        <table style="border-collapse:collapse;min-width:500px;">
            <tr style="background:#eeeeee;">
                <th style="${estilo}text-align:left;">${encabezado}</th>
            </tr>
            ${filas}
        </table>"""
    }

    // ---------- Sección 1: tabla de estatus (validaciones + ENVIOCC) ----------
    def seccion1 = ''
    if (resultados || ecEjecuto) {
        def filasVal = resultados.collect { r ->
            def color = (r.estatus == 'OK') ? '#2e7d32' : '#c62828'
            """
            <tr>
                <td style="${estilo}text-align:center;">${r.comId ?: '-'}</td>
                <td style="${estilo}">${r.descripcion ?: r.archivo}</td>
                <td style="${estilo}text-align:center;font-weight:bold;color:${color};">${r.estatus}</td>
            </tr>"""
        }.join('')

        def filaEC = ''
        if (ecEjecuto) {
            def estatusEC = (ec.estado == 'OK') ? 'OK' : 'ERROR'
            def colorEC   = (estatusEC == 'OK') ? '#2e7d32' : '#c62828'
            filaEC = """
            <tr>
                <td style="${estilo}text-align:center;">${ec.comId ?: '-'}</td>
                <td style="${estilo}">${ec.descripcion ?: (ec.titulo ?: 'ENVIOCC')}</td>
                <td style="${estilo}text-align:center;font-weight:bold;color:${colorEC};">${estatusEC}</td>
            </tr>"""
        }

        seccion1 = """
        <p>Se adjunta el status de los archivos de ${nombres}</p>

        <table style="border-collapse:collapse;min-width:500px;">
            <tr style="background:#eeeeee;">
                <th style="${estilo}">COM_ID</th>
                <th style="${estilo}text-align:left;">DESCRIPCION</th>
                <th style="${estilo}">ESTATUS</th>
            </tr>
            ${filasVal}
            ${filaEC}
        </table>"""
    }

    // ---------- Sección 2: SIN_APROBADO (solo si hay algo que reportar) ----------
    def seccion2 = ''
    if (haySA) {
        def detalle = ''

        if (sa.estado == 'CSV_NO_ENCONTRADO') {
            def motivo = sa.rutaInaccesible
                ? "La ruta <b>${sa.ruta}</b> no es accesible."
                : "No se encontró el archivo <b>${sa.patronCsv}</b> en la ruta <b>${sa.ruta}</b>."
            detalle = """
            <p>${motivo}</p>
            <p>Se detiene la validación de ${sa.titulo}.</p>"""

        } else if (sa.estado == 'ERROR_PROCESO') {
            detalle = """
            <p>Ocurrió un error al procesar ${sa.titulo}:</p>
            <p style="color:#c62828;">${sa.mensaje}</p>"""

        } else {
            def filasSA = sa.faltantes.collect { f ->
                """
                <tr>
                    <td style="${estilo}">${f.numero}</td>
                    <td style="${estilo}">${f.pdf}</td>
                </tr>"""
            }.join('')

            def avisoRuta = sa.rutaPdfInaccesible ? "<p><b>Atención:</b> la ruta de PDFs ${sa.rutaPdf} no es accesible.</p>" : ''

            detalle = """
            <p>Archivo validado: <b>${sa.nombreCsv}</b></p>
            <ul>
                <li>PDFs encontrados: <b>${sa.totalPdf}</b></li>
                <li>Filas eliminadas por "${sa.valorExcluir}": <b>${sa.eliminadas}</b></li>
            </ul>
            ${avisoRuta}
            <p>Los siguientes PDFs no se encontraron en la columna ${sa.columnaSiniestro} del archivo:</p>

            <table style="border-collapse:collapse;min-width:500px;">
                <tr style="background:#eeeeee;">
                    <th style="${estilo}text-align:left;">NÚMERO</th>
                    <th style="${estilo}text-align:left;">PDF</th>
                </tr>
                ${filasSA}
            </table>"""
        }

        seccion2 = """
        <p style="margin-top:28px;"><b>Validación ${sa.titulo ?: 'SIN_APROBADO'}</b></p>
        ${detalle}"""
    }

    // ---------- Sección 3: detalle de ENVIOCC (solo si hay error) ----------
    def seccion3 = ''
    if (hayEC) {
        def detalleEC = ''

        if (ec.estado == 'CSV_NO_ENCONTRADO') {
            def motivo = ec.rutaInaccesible
                ? "La ruta <b>${ec.ruta}</b> no es accesible."
                : "No se encontró el archivo <b>${ec.patronCsv}</b> en la ruta <b>${ec.ruta}</b>."
            detalleEC = """
            <p>${motivo}</p>
            <p>Se detiene la validación de ${ec.titulo}.</p>"""

        } else if (ec.estado == 'RUTA_PDF_NO_ACCESIBLE') {
            detalleEC = """
            <p>Archivo validado: <b>${ec.nombreCsv}</b></p>
            <p>La ruta de PDFs <b>${ec.rutaPdf}</b> no es accesible, no se pudo comparar.</p>"""

        } else if (ec.estado == 'ERROR_PROCESO') {
            detalleEC = """
            <p>Ocurrió un error al procesar ${ec.titulo}:</p>
            <p style="color:#c62828;">${ec.mensaje}</p>"""

        } else {
            def faltan = ''
            if (ec.faltanPdf) {
                faltan = """
                <p style="margin-top:20px;"><b>FALTAN PDF en la ruta</b> (${ec.faltanPdf.size()}): están en la columna ${ec.columna} del archivo, pero no existe el PDF.</p>
                ${tabla('NOMBRE', ec.faltanPdf)}
                <p><b>Acción: generar ticket (faltan PDF).</b></p>"""
            }

            def sobran = ''
            if (ec.sobranPdf) {
                sobran = """
                <p style="margin-top:20px;"><b>SOBRAN PDF en la ruta</b> (${ec.sobranPdf.size()}): existen en la ruta, pero no están en la columna ${ec.columna} del archivo (faltan en el archivo).</p>
                ${tabla('NOMBRE', ec.sobranPdf)}
                <p><b>Acción: generar ticket solicitando el CSV para diferenciar.</b></p>"""
            }

            detalleEC = """
            <p>Archivo validado: <b>${ec.nombreCsv}</b></p>
            <ul>
                <li>Registros en el archivo (${ec.columna}): <b>${ec.totalArchivo}</b></li>
                <li>PDFs en la ruta: <b>${ec.totalRuta}</b></li>
            </ul>
            ${faltan}
            ${sobran}"""
        }

        seccion3 = """
        <p style="margin-top:28px;"><b>Detalle ${ec.titulo ?: 'ENVIOCC'}</b></p>
        ${detalleEC}"""
    }

    // ---------- Adjuntos: CSV encontrados (validaciones, SIN_APROBADO limpio y ENVIOCC) ----------
    // Están en el workspace con su nombre original; solo viajan en el correo, no se archivan en Jenkins
    def adjuntos = []
    resultados.each { r ->
        if (r.adjunto) {
            adjuntos << [nombre: r.adjunto, nota: '']
        }
    }
    if (sa.archivoLimpio) {
        adjuntos << [nombre: sa.archivoLimpio,
                     nota:   " (sin las filas con \"${sa.valorExcluir}\": ${sa.eliminadas} eliminadas, ${sa.conservadas} restantes)"]
    }
    if (ec.adjunto) {
        adjuntos << [nombre: ec.adjunto, nota: '']
    }

    def adjunto = ''
    if (adjuntos) {
        def items = adjuntos.collect { a -> "<li><b>${a.nombre}</b>${a.nota}</li>" }.join('')
        adjunto = """
        <p style="margin-top:20px;">Se adjuntan los archivos encontrados (${adjuntos.size()}):</p>
        <ul>${items}</ul>"""
    }

    def html = """
    <html>
    <body style="font-family:Arial,sans-serif;font-size:14px;color:#222;">
        <p>Estimados buenos días,</p>
        ${seccion1}
        ${adjunto}
        ${seccion2}
        ${seccion3}
        <p>Saludos.</p>
    </body>
    </html>"""

    // Copia (CC): emailext los toma del mismo campo "to" con el prefijo cc:
    def copia = (config.copia ?: '').split(',').collect { it.trim() }.findAll { it }
    def para  = ([config.destinatarios] + copia.collect { "cc:${it}" }).join(',')

    emailext(
        to:                 para,
        subject:            "${config.asuntoPrefijo} ${nombres} ${config.asuntoSufijo} - ${fecha}",
        mimeType:           'text/html',
        body:               html,
        attachmentsPattern: adjuntos.collect { it.nombre }.join(',')    // rutas relativas al workspace; vacío = sin adjuntos
    )

    echo "Correo enviado a: ${config.destinatarios}" +
         (copia ? " | CC: ${copia.join(', ')}" : '') +
         (adjuntos ? " | adjuntos (${adjuntos.size()}): ${adjuntos.collect { it.nombre }.join(', ')}" : '')
}
