// validacion: resultado de validar_archivos() → [fecha, horario, descripcion, resultados]
void call(Map validacion) {
    def resultados = validacion.resultados ?: []
    def fecha      = validacion.fecha ?: ''
    def nombres    = resultados.collect { it.archivo }.join(', ')

    // Horario de esta ejecución (ej. "11:15 - rutas específicas"); TODOS solo en pruebas manuales
    def horarioTexto = validacion.horario
        ? "${validacion.horario}${validacion.descripcion ? ' - ' + validacion.descripcion : ''}"
        : ''

    def estilo  = 'border:1px solid #999;padding:8px 12px;'
    def colores = [OK: '#2e7d32', ERROR: '#c62828', PAUSADO: '#e65100']

    // Una fila por ruta; la celda PROCESO ocupa todas las filas de su proceso (rowspan)
    def filas = resultados.collect { r ->
        def n = r.rutas.size()
        def filasProceso = []
        for (int i = 0; i < n; i++) {
            def x       = r.rutas[i]
            def color   = colores[x.estatus] ?: '#c62828'
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

        <p>Se adjunta el status de los archivos de ${nombres}${horarioTexto ? " (validación de las ${horarioTexto})" : ''}</p>

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
        subject:  "${config.asuntoPrefijo} ${nombres} ${config.asuntoSufijo} - ${fecha}${validacion.horario ? ' ' + validacion.horario : ''}",
        mimeType: 'text/html',
        body:     html
    )

    echo "Correo enviado a: ${config.destinatarios}" + (copia ? " | CC: ${copia.join(', ')}" : '')
}
