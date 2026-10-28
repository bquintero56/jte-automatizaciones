// Registra el cron diario a config.hora (HH:mm) en config.zonaHoraria. Cada ejecución corre todas las consultas de la config.
void call() {
    def hora = String.valueOf(config.hora).trim()

    // HH:mm válido (0-23 : 0-59)
    def p = hora.split(':')
    if (!(hora ==~ /\d{1,2}:\d{2}/) || p[0].toInteger() > 23 || p[1].toInteger() > 59) {
        error "Hora no válida en la configuración: '${config.hora}'. Formato esperado HH:mm (ej. 09:00)"
    }

    def expresion = "TZ=${config.zonaHoraria}\n${p[1].toInteger()} ${p[0].toInteger()} * * *"

    properties([
        pipelineTriggers([cron(expresion)])
    ])

    echo "Ejecución diaria programada a las ${hora} (${config.zonaHoraria})"
}
