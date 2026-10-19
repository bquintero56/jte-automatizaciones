void call(List resultados) {
    def fecha   = resultados ? resultados[0].fecha : ''
    def nombres = resultados.collect { it.archivo }.join(', ')

    def estilo = 'border:1px solid #999;padding:8px 12px;'

    // Una fila por ruta; la celda PROCESO ocupa todas las filas de su proceso (rowspan)
    def filas = resultados.collect { r ->
        def n = r.rutas.size()
        def filasProceso = []
        for (int i = 0; i < n; i++) {
            def x       = r.rutas[i]
            def color   = (x.estatus == 'OK') ? '#2e7d32' : '#c62828'
            def proceso = (i == 0) ? """<td rowspan="${n}" style="${estilo}vertical-align:middle;">${r.proceso}</td>""" : ''
            filasProceso << """
        <tr>
            ${proceso}
            <td style="${estilo}">${x.ruta}</td>
            <td style="${estilo}text-align:center;font-weight:bold;color:${color};">${x.estatus}</td>
        </tr>"""
        }
        filasProceso.join('')
    }.join('')

    def html = """
    <html>
    <body style="font-family:Arial,sans-serif;font-size:14px;color:#222;">
        <p>Estimados buenos días,</p>

        <p>Se adjunta el status de los archivos de ${nombres}</p>

        <table style="border-collapse:collapse;min-width:500px;">
            <tr style="background:#eeeeee;">
                <th style="${estilo}text-align:left;">PROCESO</th>
                <th style="${estilo}text-align:left;">RUTA</th>
                <th style="${estilo}">ESTATUS</th>
            </tr>
            ${filas}
        </table>

        <p>Saludos.</p>
    </body>
    </html>"""

    emailext(
        to:       config.destinatarios,
        subject:  "${config.asuntoPrefijo} ${nombres} ${config.asuntoSufijo} - ${fecha}",
        mimeType: 'text/html',
        body:     html
    )

    echo "Correo enviado a: ${config.destinatarios}"
}
