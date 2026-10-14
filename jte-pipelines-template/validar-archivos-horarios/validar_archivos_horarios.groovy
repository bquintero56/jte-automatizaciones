def resultados  = []
def resultadoSA = [:]
def resultadoEC = [:]

programar_ejecuciones()

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                stage('SSH: validar archivos') {
                    resultados = validar_archivos_horario(params.HORA)
                }

                stage('SIN_APROBADO: validar y procesar') {
                    try {
                        resultadoSA = procesar_sin_aprobado(params.HORA)
                    }
                    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                        throw e     // aborto manual o timeout: no se convierte en ERROR_PROCESO
                    }
                    catch (Exception e) {
                        echo "Error en SIN_APROBADO: ${e.message}"
                        resultadoSA = [estado: 'ERROR_PROCESO', titulo: 'SIN_APROBADO', mensaje: e.message]
                        unstable("Error en SIN_APROBADO: ${e.message}")
                    }
                }

                stage('ENVIOCC: validar y comparar') {
                    try {
                        resultadoEC = procesar_enviocc(params.HORA)
                    }
                    catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                        throw e     // aborto manual o timeout: no se convierte en ERROR_PROCESO
                    }
                    catch (Exception e) {
                        echo "Error en ENVIOCC: ${e.message}"
                        resultadoEC = [estado: 'ERROR_PROCESO', titulo: 'ENVIOCC', mensaje: e.message]
                        unstable("Error en ENVIOCC: ${e.message}")
                    }
                }

                stage('Notificación: enviar correo') {
                    def haySA = (resultadoSA.estado == 'CSV_NO_ENCONTRADO' ||
                                 resultadoSA.estado == 'PDF_SIN_REGISTRO' ||
                                 resultadoSA.estado == 'ERROR_PROCESO')
                    def ecEjecuto = (resultadoEC.estado && resultadoEC.estado != 'OMITIDO')

                    if (resultados || haySA || ecEjecuto) {
                        enviar_correo_resultados(resultados, resultadoSA, resultadoEC)
                    } else {
                        echo "Nada que reportar en esta ejecución, no se envía correo"
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
