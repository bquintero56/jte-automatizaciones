fields {
    required {
        zonaHoraria     = String
        hora            = String
        timeoutSegundos = Integer
        codigosOk       = List
        estadoSiFalla   = String
        consultas       = List      // cada una: nombre, url, metodo; opcional: headers, body, auth (basic | bearer), credencial, codigosOk, proxy
    }
    optional {
        proxy           = String    // ej. 'http://proxy.empresa.cl:8080'; vacío o ausente = sin proxy
        credencialProxy = String    // ID de credencial Username with password si el proxy pide usuario y clave
    }
}
