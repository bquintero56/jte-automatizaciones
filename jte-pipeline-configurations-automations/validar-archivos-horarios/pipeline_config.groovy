jte {
    allow_scm_jenkinsfile = false
    pipeline_template = 'validar-archivos-horarios/validar_archivos_horarios.groovy'   // AJUSTAR a la ruta real de tu template
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
    validacion_horarios {
        host           = '10.20.30.40'
        user           = 'svc_jenkins'
        runAsUser      = 'usr_datos'
        zonaHoraria    = 'America/Bogota'
        estadoSiFaltan = 'SUCCESS'              // SUCCESS, UNSTABLE o FAILURE

        // Ruta con parte variable: {mes} se reemplaza por AAAAMM (202610, 202611...)
        rutaBase       = '/Proyecto/M/BACKUP/{mes}'
        formatoMes     = '%Y%m'

        // Fecha que va en el nombre del archivo
        formatoFecha   = '%Y-%m-%d'             // 2026-10-07
        diasAtras      = 0                      // 0 = hoy, 1 = ayer (para pruebas; compartido por los 3 procesos)

        // Tolerancia: el job puede arrancar hasta N minutos después de la hora
        ventanaMinutos = 20

        // Todos los archivos llevan números + "_" al inicio
        prefijoNombre  = '*_'

        // AJUSTAR: nombres, COM_ID y DES (descripción) reales de cada archivo
        archivos = [
            [COM_ID: '23', DES: 'Descripción de devoluciones', nombre: 'DEVOLUCION_{fecha}.csv', hora: '15:00'],
            [COM_ID: '24', DES: 'Descripción del contador',    nombre: 'CONTADOR_{fecha}.csv',   hora: '15:00'],
            [COM_ID: '25', DES: 'Descripción archivo 3',       nombre: 'ARCHIVO_3_{fecha}.csv',  hora: '15:00'],
            [COM_ID: '26', DES: 'Descripción archivo 4',       nombre: 'ARCHIVO_4_{fecha}.csv',  hora: '15:00'],
            [COM_ID: '27', DES: 'Descripción archivo 5',       nombre: 'ARCHIVO_5_{fecha}.csv',  hora: '15:00'],
            [COM_ID: '28', DES: 'Descripción archivo 6',       nombre: 'ARCHIVO_6_{fecha}.csv',  hora: '15:00'],
            [COM_ID: '29', DES: 'Descripción archivo 7',       nombre: 'ARCHIVO_7_{fecha}.csv',  hora: '15:00'],
            [COM_ID: '30', DES: 'Descripción archivo 8',       nombre: 'ARCHIVO_8_{fecha}.csv',  hora: '15:00'],
            [COM_ID: '31', DES: 'Descripción archivo 9',       nombre: 'ARCHIVO_9_{fecha}.csv',  hora: '15:00'],
            [COM_ID: '32', DES: 'Descripción archivo 10',      nombre: 'ARCHIVO_10_{fecha}.csv', hora: '15:00']
            // Opcional por archivo: ruta: '/OTRA/RUTA/{mes}' para sobrescribir rutaBase
        ]

        // Tarea SIN_APROBADO (CSV y PDFs están en rutaBase)
        sinAprobado {
            hora             = '15:00'
            textoFijo        = '_SIN_APROBADO_'
            formatoFechaCsv  = '%Y-%m-%d'       // 2026-10-08
            formatoFechaPdf  = '%Y%m%d'         // 20261008
            separador        = ';'              // separador del CSV (',' o ';')
            columnaTipo      = 'TIPO_LIQUIDADOR'
            valorExcluir     = 'Liquidador externo'
            columnaSiniestro = 'NUMERO_SINIESTRO'
        }

        // Tarea ENVIOCC (el CSV está en rutaBase, la carpeta del mes)
        enviocc {
            COM_ID          = '67'
            DES             = 'Descripción de ENVIOCC'     // texto que se ve en la tabla del correo
            hora            = '15:00'
            textoCsv        = 'ENVIOCC'
            formatoFechaCsv = '%Y-%m-%d'          // 2026-10-09
            separador       = ','                 // separador del CSV (',' o ';')
            columna         = 'FILENAMEFIELD'

            rutaPdf         = '/Sistema/Proyecto/test/PDF'
            formatoFechaPdf = '%d_%m_%Y'          // 09_10_2026
            textoPdf        = 'CCTEST'
            extensionPdf    = '.pdf'
        }
    }

    notificacion_correo {
        destinatarios = 'persona1@empresa.com,persona2@empresa.com'
        asuntoPrefijo = 'Monitoreo activo Seguimiento'
        asuntoSufijo  = '360'
    }
}
