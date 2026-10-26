// Correo del pipeline 3 (validar-rutas-especificas): una fila por ruta con OK / ERROR.
void call(List resultados) {
    def fecha   = resultados ? resultados[0].fecha : ''
    def nombres = resultados.collect { it.archivo }.unique().join(', ')

    def estilo = 'border:1px solid #999;padding:8px 12px;'

    def filas = resultados.collect { r ->
        def color = (r.estatus == 'OK') ? '#2e7d32' : '#c62828'
        """
        <tr>
            <td style="${estilo}">${r.proceso}</td>
            <td style="${estilo}">${r.ruta}</td>
            <td style="${estilo}text-align:center;font-weight:bold;color:${color};">${r.estatus}</td>
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
                <th style="${estilo}text-align:left;">RUTA</th>
                <th style="${estilo}">ESTATUS</th>
            </tr>
            ${filas}
        </table>

        <p>Saludos.</p>
    </body>
    </html>"""

    // Copia (CC): emailext los toma del mismo campo "to" con el prefijo cc:
    def copia = (config.copia ?: '').split(',').collect { it.trim() }.findAll { it }
    def para  = ([config.destinatarios] + copia.collect { "cc:${it}" }).join(',')

    emailext(
        to:       para,
        subject:  "${config.asuntoPrefijo} ${nombres} ${config.asuntoSufijo} - ${fecha}",
        mimeType: 'text/html',
        body:     html
    )

    echo "Correo enviado a: ${config.destinatarios}" + (copia ? " | CC: ${copia.join(', ')}" : '')
}
