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

        // Proxy para salir a las APIs (curl --proxy). '' = sin proxy. Una consulta puede tener su propio proxy: (o '' para ir directo).
        proxy           = 'http://proxy.empresa.cl:8080'   // AJUSTAR: host:puerto del proxy de la empresa
        credencialProxy = ''                               // ID de credencial Username with password si el proxy pide usuario; '' = sin usuario

        // Una entrada por request del proyecto SoapUI "Consentimiento" (Consentimiento-soapui-project.xml):
        //   nombre     = texto de la columna SERVICIO del correo
        //   url        = <con:originalUri> del request, tal cual (ya trae el endpoint + el path del recurso)
        //   metodo     = atributo method del <con:method> (GET, POST...; SOAP siempre es POST)
        //   headers    = mapa de headers (para SOAP: Content-Type text/xml y SOAPAction)
        //   body       = contenido de <con:request> (JSON o sobre SOAP); opcional
        // Opcional: auth = 'basic' o 'bearer' + credencial = ID de la credencial en Jenkins
        //   (basic → Username with password; bearer → Secret text). NUNCA pongas tokens ni contraseñas aquí: el repo es público.
        //   El perfil OAuth 2.0 "GENERALES" de SoapUI (token en el HEADER) = auth 'bearer' con el accessToken en la credencial.
        // Opcional: codigosOk = [200, 201] para sobrescribir el general en esa consulta.
        // Cada bloque [ ... ] es UNA consulta (un request del XML) y lleva UNA sola url: su <con:originalUri>.
        consultas = [
            // ---- Consulta 1: <con:resource name="ReciboConsentimiento.json"> ----
            [
                nombre:  'ReciboConsentimiento',
                url:     'https://api-entesCIA-ntimiento.json',                 // AJUSTAR: <con:originalUri> real
                metodo:  'POST',
                headers: ['Content-Type': 'application/json'],
                body:    '''{
  "Consentimiento": {
    "CodigoID": "000001",
    "Llave": "76",
    "Fecha": "2022/03/10 16:24:02",
    "TipoIdentificacion": "RUT",
    "NumeroIdentificacion": "73-8",
    "NombreRazonSocial": "JREZ",
    "Email": "ejemplo@chile.cl",
    "Fono": "+0000",
    "InformacionConsentir": {
      "Rut": "-8",
      "RazonSocial": "Generales",
      "Grupo": "GENERALES",
      "Asegurado": {
        "TipoIdentificacion": "RUT",
        "NumeroIdentificacion": "78",
        "NombreRazonSocial": "RINO"
      },
      "Contratante": [
        {
          "TipoIdentificacion": "RUT",
          "NumeroIdentificacion": "96837640-3",
          "NombreRazonSocial": "generales"
        }
      ],
      "DatosCorredorAsesor": {
        "Corredor": [
          {
            "TipoIdentificacion": "RUT",
            "NumeroIdentificacion": "132456789-K",
            "NombreRazonSocial": "ROD"
          }
        ]
      },
      "DetallePoliza": {
        "Pol": [
          {
            "Codigo": "12345",
            "Ramo": "00012",
            "FechaInicio": "2022/07/12",
            "FechaTerminoIndeterminada": "NO",
            "FechaTermino": "2022/08/14",
            "Clausulas": [
              {
                "Codigo": "123456",
                "Ramo": "00012",
                "FechaInicio": "2022/01/12",
                "FechaTerminoIndeterminada": "SI",
                "FechaTermino": "2022/01/13"
              }
            ]
          }
        ]
      }
    }
  }
}''',
                auth:       'bearer',
                credencial: 'token-oauth-generales'                         // AJUSTAR: ID de la credencial Secret text
            ],
            // ---- Consulta 2: <con:resource name="dad.json"> (body {}) ----
            [
                nombre:     'Cad',
                url:        'https://api-st.testnet.com/sCA/Coidad.json',    // AJUSTAR: <con:originalUri> real
                metodo:     'POST',
                headers:    ['Content-Type': 'application/json'],
                body:       '{}',
                auth:       'bearer',
                credencial: 'token-oauth-generales'
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
