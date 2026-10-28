jte {
    allow_scm_jenkinsfile = false
    pipeline_template = 'consultar-servicios/consultar_servicios.groovy'   // AJUSTAR a la ruta real de tu template
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
    consultas_servicios {
        zonaHoraria     = 'America/Santiago'   // hora del cron y fecha del correo en hora de Chile
        hora            = '08:00'              // AJUSTAR: ejecución diaria (HH:mm en zonaHoraria)
        timeoutSegundos = 30                   // tiempo máximo de respuesta por consulta
        codigosOk       = [200]                // códigos HTTP que cuentan como OK
        estadoSiFalla   = 'SUCCESS'            // SUCCESS, UNSTABLE o FAILURE

        // AJUSTAR: una entrada por consulta, copiando los datos del proyecto de SoapUI:
        //   nombre     = texto de la columna SERVICIO del correo
        //   url        = <con:originalUri> / endpoint de la consulta
        //   metodo     = GET, POST, PUT... (SOAP siempre es POST)
        //   headers    = mapa de headers (Content-Type, SOAPAction...)
        //   body       = cuerpo de la petición (JSON o sobre SOAP); opcional
        // Opcional: auth = 'basic' o 'bearer' + credencial = ID de la credencial en Jenkins
        //   (basic → Username with password; bearer → Secret text). NUNCA pongas tokens ni contraseñas aquí: el repo es público.
        // Opcional: codigosOk = [200, 201] para sobrescribir el general en esa consulta.
        consultas = [
            [
                nombre:  'Consentimiento',
                url:     'https://api.ejemplo.com/consentimiento',
                metodo:  'POST',
                headers: ['Content-Type': 'application/json'],
                body:    '''{
                    "Consentimiento": {
                        "codigo": "AJUSTAR"
                    }
                }''',
                auth:       'bearer',
                credencial: 'token-api-consentimiento'
            ],
            [
                nombre:  'Consulta SOAP',
                url:     'https://api.ejemplo.com/servicio',
                metodo:  'POST',
                headers: [
                    'Content-Type': 'text/xml; charset=UTF-8',
                    'SOAPAction':   'AJUSTAR'
                ],
                body:    '''<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <!-- AJUSTAR: el cuerpo de la consulta -->
                    </soapenv:Body>
                </soapenv:Envelope>''',
                auth:       'basic',
                credencial: 'usuario-servicio-soap'
            ]
        ]
    }

    notificacion_correo {
        destinatarios = 'persona1@empresa.com,persona2@empresa.com'
        copia         = ''                                          // CC separados por coma (opcional; '' = sin copia)
        asuntoPrefijo = 'Monitoreo activo Seguimiento'
        asuntoSufijo  = '360'
    }
}
