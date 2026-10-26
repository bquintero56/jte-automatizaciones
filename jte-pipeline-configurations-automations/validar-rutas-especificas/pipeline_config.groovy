jte {
    allow_scm_jenkinsfile = false
    pipeline_template = 'validar-rutas-especificas/validar_rutas_especificas.groovy'   // AJUSTAR a la ruta real de tu template
}

// El template usa execution.time, execution.units y jenkins_agent_label
keywords {
    execution {
        time  = 10
        units = 'MINUTES'
    }
    jenkins_agent_label = 'linux-pro'
}

libraries {
    validacion_rutas {
        host           = '10.20.30.40'
        user           = 'svc_jenkins'
        runAsUser      = 'usr_datos'
        zonaHoraria    = 'America/Santiago'   // hora del cron y fecha de los archivos en hora de Chile
        hora           = '18:30'              // ejecución diaria (HH:mm en zonaHoraria)
        formatoFecha   = '%Y%m%d'             // fecha en el nombre del archivo: año mes día (20260926)
        diasAtras      = 0                    // 0 = archivos del día de ejecución (producción), 1 = día anterior (pruebas)
        estadoSiFaltan = 'SUCCESS'            // SUCCESS, UNSTABLE o FAILURE

        // AJUSTAR: las 4 rutas reales. Por archivo: nombre (admite comodín *, {fecha} = fecha del día) y ruta.
        // Opcional: proceso = texto de la columna PROCESO del correo (por defecto, el nombre sin fecha ni extensión).
        archivos = [
            [nombre: 'testSiniestro_{fecha}.zip', ruta: '/PROYECTO/Siniestros/RUTA1'],
            [nombre: 'testSiniestro_{fecha}.zip', ruta: '/PROYECTO/Siniestros/RUTA2'],
            [nombre: 'testSiniestro_{fecha}.zip', ruta: '/PROYECTO/Siniestros/RUTA3'],
            [nombre: 'testSiniestro_{fecha}.zip', ruta: '/PROYECTO/Siniestros/RUTA4']
        ]
    }

    notificacion_correo {
        destinatarios = 'persona1@empresa.com,persona2@empresa.com'
        copia         = ''                                          // CC separados por coma (opcional; '' = sin copia)
        asuntoPrefijo = 'Monitoreo activo Seguimiento'
        asuntoSufijo  = '360'
    }
}
