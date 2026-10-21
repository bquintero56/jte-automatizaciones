def validacion = [:]

// Cron con los horarios de la config (horaRutaGeneral + horaEspecifica, en zonaHoraria) y parámetro HORARIO para pruebas
programar_validaciones()

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                stage('SSH: validar archivos') {
                    validacion = validar_archivos(params.HORARIO)
                }

                stage('Notificación: enviar correo') {
                    // Un correo por ejecución, con lo validado en ese horario
                    if (validacion.resultados) {
                        enviar_correo_validacion_archivos(validacion)
                    } else {
                        echo "Nada programado en este horario, no se envía correo"
                    }
                }
            }
            catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                throw e     // conserva el estado ABORTED
            }
            catch (Exception e) {
                error("${e.message ?: e.toString()}")
            }
            finally {
                clean_job_workspace()
            }
        }
    }
}
