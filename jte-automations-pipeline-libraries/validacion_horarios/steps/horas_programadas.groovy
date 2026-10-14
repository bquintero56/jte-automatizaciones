// Horas únicas (HH:mm) de las tres tareas: archivos, SIN_APROBADO y ENVIOCC
List call() {
    def horas = config.archivos.collect { it.hora }
    if (config.sinAprobado?.hora) {
        horas << config.sinAprobado.hora
    }
    if (config.enviocc?.hora) {
        horas << config.enviocc.hora
    }
    return horas.unique()
}
