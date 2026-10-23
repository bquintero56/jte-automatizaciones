jte {
    allow_scm_jenkinsfile = false
    pipeline_template = 'automation-pipelines/autIN/autIN_template.groovy'   // AJUSTAR a la ruta real de tu template
}

keywords {
    execution {
        time  = 10
        units = 'MINUTES'
    }
    jenkins_agent_label = 'linux-pro'
    job_environments = '''
        JAVA_OPTS=-Xmx512m
        MAVEN_OPTS=-Dmaven.wagon.http.ssl.insecure=true -Dmaven.resolver.transport=wagon
        '''
    log_rotator = '20'
}

libraries {
    validacion_archivos {
        host           = '10.20.30.40'
        user           = 'svc_jenkins'
        runAsUser      = 'usr_datos'
        formatoFecha   = '%Y%m%d'
        zonaHoraria    = 'America/Santiago'   // horas del cron y fecha de los archivos en hora de Chile
        diasAtras      = 0                    // 0 = archivos del día de ejecución (producción), 1 = día anterior (pruebas)
        estadoSiFaltan = 'SUCCESS'            // SUCCESS, UNSTABLE o FAILURE (PAUSADO no cuenta como faltante)

        // Horarios (HH:mm en zonaHoraria). Cada ejecución envía su propio correo con lo que validó.
        horaRutaGeneral = '10:15'             // ruta general: siempre los 5 archivos (sin pausa)

        rutaGeneral    = '/data/entrada'      // donde deben estar los 5 archivos

        // Por archivo: horaEspecifica = cuándo se valida su ruta específica; estado = 'ACTIVO' o 'PAUSADO'
        // (PAUSADO: no se busca en la ruta específica y en el correo sale PAUSADO; la ruta general sí se valida)
        archivos = [
            [
                nombre:           'REPORTE_{fecha}.csv',                // en la ruta general (exacto)
                rutaEspecifica:   '/PROYECTO/Report/HOLA/Procesando',
                patronEspecifico: 'REPORTE_{fecha}_*.csv',              // en su ruta (con comodín)
                horaEspecifica:   '11:15',
                estado:           'ACTIVO'
            ],
            [
                nombre:           'ARCHIVO_DOS_{fecha}.csv',
                rutaEspecifica:   '/PROYECTO/Report/RUTA2',
                patronEspecifico: 'ARCHIVO_DOS_{fecha}_*.csv',
                horaEspecifica:   '11:15',
                estado:           'ACTIVO'
            ],
            [
                nombre:           'ARCHIVO_TRES_{fecha}.csv',
                rutaEspecifica:   '/PROYECTO/Report/RUTA3',
                patronEspecifico: 'ARCHIVO_TRES_{fecha}_*.csv',
                horaEspecifica:   '18:45',
                estado:           'ACTIVO'
            ],
            [
                nombre:           'ARCHIVO_CUATRO_{fecha}.csv',
                rutaEspecifica:   '/PROYECTO/Report/RUTA4',
                patronEspecifico: 'ARCHIVO_CUATRO_{fecha}_*.csv',
                horaEspecifica:   '18:45',
                estado:           'ACTIVO'
            ],
            [
                nombre:           'ARCHIVO_CINCO_{fecha}.csv',
                rutaEspecifica:   '/PROYECTO/Report/RUTA5',
                patronEspecifico: 'ARCHIVO_CINCO_{fecha}_*.csv',
                horaEspecifica:   '18:45',
                estado:           'ACTIVO'
            ]
        ]
    }

    notificacion_correo {
        destinatarios = 'persona1@empresa.com,persona2@empresa.com'
        copia         = 'copia1@empresa.com,copia2@empresa.com'   // CC (opcional; '' = sin copia)
        asuntoPrefijo = 'Monitoreo activo Seguimiento'
        asuntoSufijo  = '360'
    }
}
