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

        // Tokens: UNA credencial Secret file con una línea por token (NOMBRE=token). Cada consulta indica su NOMBRE en token:.
        // En el repo solo van los nombres, nunca los valores (el repo es público).
        credencialTokens = 'tokens-sics'                   // AJUSTAR: ID del Secret file creado en Jenkins

        // Una entrada por consulta (un XML / request de SoapUI). Cada bloque [ ... ] es UNA consulta con UNA url:
        //   nombre  = texto de la columna SERVICIO del correo
        //   url     = <con:originalUri> del request, tal cual (ya trae el endpoint + el path del recurso)
        //   metodo  = atributo method del <con:method> (GET, POST...; SOAP siempre es POST)
        //   headers = mapa de headers (Content-Type = mediaType del <con:request>; para SOAP: text/xml y SOAPAction)
        //   body    = contenido de <con:request> (JSON o sobre SOAP); opcional
        //   auth    = 'bearer' (OAuth 2.0 de SoapUI, token en el HEADER)
        //   token   = NOMBRE de la línea en el Secret file credencialTokens (ej. CF_VIDA=eyJ... → token: 'CF_VIDA')
        // Alternativas: credencial: 'id-secret-text' en vez de token (una credencial propia), o auth 'basic' + credencial
        // Username with password. Opcional: codigosOk = [200, 201] para sobrescribir el general; proxy propio.
        consultas = [
            // ---- Consulta 1 ----
            [
                nombre:  'Consentimiento CF_VIDA',
                url:     'https://api.ejemplo.cl/cf-vida/ReciboConsentimiento.json',   // AJUSTAR: <con:originalUri>
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
                auth:    'bearer',
                token:   'CF_VIDA'                                                     // AJUSTAR: nombre en el Secret file
            ],
            // ---- Consulta 2 ----
            [
                nombre:  'Consentimiento Generales',
                url:     'https://api.ejemplo.cl/generales/ReciboConsentimiento.json', // AJUSTAR: <con:originalUri>
                metodo:  'POST',
                headers: ['Content-Type': 'application/json'],
                body:    '''{
  "Consentimiento": {
    "CodigoID": "AJUSTAR: pega aquí el contenido de <con:request> de este XML"
  }
}''',
                auth:    'bearer',
                token:   'GENERALES'
            ],
            // ---- Consulta 3 ----
            [
                nombre:  'Consentimiento VIDA',
                url:     'https://api.ejemplo.cl/vida/ReciboConsentimiento.json',      // AJUSTAR: <con:originalUri>
                metodo:  'POST',
                headers: ['Content-Type': 'application/json'],
                body:    '''{
  "Consentimiento": {
    "CodigoID": "AJUSTAR: pega aquí el contenido de <con:request> de este XML"
  }
}''',
                auth:    'bearer',
                token:   'VIDA'
            ]
            // ---- Consultas 4, 5 y 6: copiar un bloque [ ... ] de arriba, poner una coma después del anterior y ajustar ----
        ]
    }

    notificacion_correo {
        destinatarios = 'persona1@empresa.com,persona2@empresa.com'
        copia         = ''                                          // CC separados por coma (opcional; '' = sin copia)
        asuntoPrefijo = 'Monitoreo activo Seguimiento'
        asuntoSufijo  = '360'
    }
}
