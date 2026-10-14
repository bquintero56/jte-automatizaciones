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
        zonaHoraria    = 'America/Bogota'
        estadoSiFaltan = 'SUCCESS'          // SUCCESS, UNSTABLE o FAILURE

        rutaGeneral    = '/data/entrada'    // donde deben estar los 5 archivos

        archivos = [
            [
                nombre:           'REPORTE_{fecha}.csv',                // en la ruta general (exacto)
                rutaEspecifica:   '/PROYECTO/Report/HOLA/Procesando',
                patronEspecifico: 'REPORTE_{fecha}_*.csv'               // en su ruta (con comodín)
            ],
            [
                nombre:           'ARCHIVO_DOS_{fecha}.csv',
                rutaEspecifica:   '/PROYECTO/Report/RUTA2',
                patronEspecifico: 'ARCHIVO_DOS_{fecha}_*.csv'
            ],
            [
                nombre:           'ARCHIVO_TRES_{fecha}.csv',
                rutaEspecifica:   '/PROYECTO/Report/RUTA3',
                patronEspecifico: 'ARCHIVO_TRES_{fecha}_*.csv'
            ],
            [
                nombre:           'ARCHIVO_CUATRO_{fecha}.csv',
                rutaEspecifica:   '/PROYECTO/Report/RUTA4',
                patronEspecifico: 'ARCHIVO_CUATRO_{fecha}_*.csv'
            ],
            [
                nombre:           'ARCHIVO_CINCO_{fecha}.csv',
                rutaEspecifica:   '/PROYECTO/Report/RUTA5',
                patronEspecifico: 'ARCHIVO_CINCO_{fecha}_*.csv'
            ]
        ]
    }

    notificacion_correo {
        destinatarios = 'persona1@empresa.com,persona2@empresa.com'
        asuntoPrefijo = 'Monitoreo activo Seguimiento'
        asuntoSufijo  = '360'
    }
}
