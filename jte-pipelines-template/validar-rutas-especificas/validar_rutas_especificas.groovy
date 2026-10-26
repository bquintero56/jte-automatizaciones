def resultados = []

// Cron diario a la hora de la config (hora + zonaHoraria)
programar_validacion_rutas()

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                stage('SSH: validar rutas') {
                    resultados = validar_rutas()
                }

                stage('Notificación: enviar correo') {
                    enviar_correo_validacion_rutas(resultados)
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
