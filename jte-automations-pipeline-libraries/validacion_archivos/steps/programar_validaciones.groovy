// Registra el cron con las horas de la config (horaRutaGeneral + horaEspecifica de cada archivo) en config.zonaHoraria
// y el parámetro HORARIO (solo para pruebas manuales). Valida horas y estados antes de conectarse por SSH.
void call() {
    // HH:mm válido (0-23 : 0-59)
    def esHora = { String h ->
        if (!(h ==~ /\d{1,2}:\d{2}/)) {
            return false
        }
        def p = h.split(':')
        return p[0].toInteger() <= 23 && p[1].toInteger() <= 59
    }

    // Parámetro HORARIO: vacío (automático), TODOS o HH:mm
    def modo = params.HORARIO?.trim()
    if (modo && modo.toUpperCase() != 'TODOS' && !esHora(modo)) {
        error "Valor de HORARIO no válido: '${params.HORARIO}'. Usa vacío, TODOS o HH:mm (ej. 11:15)"
    }

    // Horas únicas: la de la ruta general y las de las rutas específicas
    def horas = [String.valueOf(config.horaRutaGeneral).trim()]
    config.archivos.each { item ->
        horas << String.valueOf(item.horaEspecifica).trim()
    }
    horas = horas.unique()

    def horasMal = horas.findAll { !esHora(it) }
    if (horasMal) {
        error "Horas no válidas en la configuración: ${horasMal.join(', ')}. Formato esperado HH:mm (revisa horaRutaGeneral y horaEspecifica)"
    }

    // Estados válidos por archivo
    def estadosMal = config.archivos.findAll { item ->
        !['ACTIVO', 'PAUSADO'].contains(String.valueOf(item.estado ?: 'ACTIVO').trim().toUpperCase())
    }
    if (estadosMal) {
        error "Estado no válido en: ${estadosMal.collect { it.nombre }.join(', ')}. Usa 'ACTIVO' o 'PAUSADO'"
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
                name: 'HORARIO',
                defaultValue: '',
                description: 'Solo para pruebas. Vacío = automático según la hora (lo que usa el cron). TODOS = ruta general y todas las específicas. HH:mm = simula ese horario (ej. 10:15, 11:15, 18:45).'
            )
        ])
    ])

    echo "Horarios programados (${config.zonaHoraria}): ${horas.join(', ')}"
}
