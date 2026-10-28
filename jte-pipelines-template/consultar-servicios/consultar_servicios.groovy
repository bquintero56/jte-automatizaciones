def consulta = [:]

// Cron diario a la hora de la config (hora + zonaHoraria)
programar_consultas()

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                stage('Consultas: ejecutar servicios') {
                    consulta = ejecutar_consultas()
                }

                stage('Notificación: enviar correo') {
                    enviar_correo_consultas(consulta)
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
