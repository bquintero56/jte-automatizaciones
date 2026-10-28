// Correo del pipeline 4 (consultar-servicios): una fila por consulta con su código HTTP y OK / ERROR.
void call(Map consulta) {
    def fecha      = consulta.fecha
    def resultados = consulta.resultados ?: []
    def nombres    = resultados.collect { it.nombre }.unique().join(', ')

    def estilo = 'border:1px solid #999;padding:8px 12px;'

    def filas = resultados.collect { r ->
        def color = (r.estatus == 'OK') ? '#2e7d32' : '#c62828'
        """
        <tr>
            <td style="${estilo}">${r.nombre}</td>
            <td style="${estilo}text-align:center;">${r.metodo}</td>
            <td style="${estilo}text-align:center;">${r.codigo}</td>
            <td style="${estilo}text-align:center;">${r.tiempo ? r.tiempo + ' s' : '-'}</td>
            <td style="${estilo}text-align:center;font-weight:bold;color:${color};">${r.estatus}</td>
        </tr>"""
    }.join('')

    // Detalle solo de las consultas con error
    def errores = resultados.findAll { it.estatus != 'OK' }
    def detalle = ''
    if (errores) {
        def items = errores.collect { "<li><b>${it.nombre}</b>: ${it.detalle}</li>" }.join('')
        detalle = """
        <p><b>Detalle de errores</b></p>
        <ul>${items}</ul>"""
    }

    def html = """
    <html>
    <body style="font-family:Arial,sans-serif;font-size:14px;color:#222;">
        <p>Estimados buenos días,</p>

        <p>Se adjunta el status de las consultas de ${nombres}</p>

        <table style="border-collapse:collapse;min-width:500px;">
            <tr style="background:#eeeeee;">
                <th style="${estilo}text-align:left;">SERVICIO</th>
                <th style="${estilo}">MÉTODO</th>
                <th style="${estilo}">CÓDIGO HTTP</th>
                <th style="${estilo}">TIEMPO</th>
                <th style="${estilo}">ESTATUS</th>
            </tr>
            ${filas}
        </table>
        ${detalle}

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
