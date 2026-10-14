void call(List resultados) {
    def fecha   = resultados ? resultados[0].fecha : ''
    def nombres = resultados.collect { it.archivo }.join(', ')

    def estilo = 'border:1px solid #999;padding:8px 12px;'

    def filas = resultados.collect { r ->
        def color = (r.estatus == 'OK') ? '#2e7d32' : '#c62828'
        def rutas = r.rutas ? r.rutas.join('<br>') : '-'

        """
        <tr>
            <td style="${estilo}">${r.proceso}</td>
            <td style="${estilo}text-align:center;font-weight:bold;color:${color};">${r.estatus}</td>
            <td style="${estilo}">${rutas}</td>
        </tr>"""
    }.join('')

    def html = """
    <html>
    <body style="font-family:Arial,sans-serif;font-size:14px;color:#222;">
        <p>Estimados buenos días,</p>

        <p>Se adjunta el status de los archivos de ${nombres}</p>

        <table style="border-collapse:collapse;min-width:500px;">
            <tr style="background:#eeeeee;">
                <th style="${estilo}text-align:left;">PROCESO</th>
                <th style="${estilo}">ESTATUS</th>
                <th style="${estilo}text-align:left;">RUTA DONDE NO SE ENCONTRÓ</th>
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
