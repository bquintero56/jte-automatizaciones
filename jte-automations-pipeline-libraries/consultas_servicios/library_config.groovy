fields {
    required {
        zonaHoraria     = String
        hora            = String
        timeoutSegundos = Integer
        codigosOk       = List
        estadoSiFalla   = String
        consultas       = List      // cada una: nombre, url, metodo; opcional: headers, body, auth (basic | bearer), credencial, codigosOk
    }
}
