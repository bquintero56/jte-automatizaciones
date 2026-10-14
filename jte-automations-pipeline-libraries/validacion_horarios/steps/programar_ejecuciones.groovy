void call() {
    // HH:mm válido (0-23 : 0-59)
    def esHora = { String h ->
        if (!(h ==~ /\d{1,2}:\d{2}/)) {
            return false
        }
        def p = h.split(':')
        return p[0].toInteger() <= 23 && p[1].toInteger() <= 59
    }

    // Parámetro HORA: vacío, TODOS o HH:mm (falla aquí con un mensaje claro, antes de conectarse por SSH)
    def modo = params.HORA?.trim()
    if (modo && modo.toUpperCase() != 'TODOS' && !esHora(modo)) {
        error "Valor de HORA no válido: '${params.HORA}'. Usa vacío, TODOS o HH:mm (ej. 15:00)"
    }

    def horas = config.archivos.collect { it.hora }
    if (config.sinAprobado?.hora) {
        horas << config.sinAprobado.hora
    }
    if (config.enviocc?.hora) {
        horas << config.enviocc.hora
    }
    horas = horas.unique()

    // Las horas de la config también deben ser HH:mm
    def horasMal = horas.findAll { !esHora(String.valueOf(it)) }
    if (horasMal) {
        error "Horas no válidas en la configuración: ${horasMal.join(', ')}. Formato esperado HH:mm"
    }

    def lineas = horas.collect { h ->
        def p = h.split(':')
        "${p[1].toInteger()} ${p[0].toInteger()} * * *"
    }

    def expresion = "TZ=${config.zonaHoraria}\n" + lineas.join('\n')

    properties([
        pipelineTriggers([cron(expresion)]),
        parameters([
            string(
                name: 'HORA',
                defaultValue: '',
                description: 'Vacío = automático según la hora actual. TODOS = ejecuta todo. HH:mm = simula esa hora (ej. 15:00).'
            )
        ])
    ])
}
