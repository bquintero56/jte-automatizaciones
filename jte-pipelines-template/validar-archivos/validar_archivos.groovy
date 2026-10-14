def resultados = []

// Ejecución diaria a la 1:00 p. m. (hora de Colombia)
properties([
    pipelineTriggers([cron('TZ=America/Bogota\n0 13 * * *')])
])

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                stage('SSH: validar archivos') {
                    resultados = validar_archivos()
                }

                stage('Notificación: enviar correo') {
                    enviar_correo_validacion_archivos(resultados)
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
