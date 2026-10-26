fields {
    required {
        host           = String
        user           = String
        runAsUser      = String
        zonaHoraria    = String
        hora           = String
        formatoFecha   = String
        diasAtras      = Integer
        estadoSiFaltan = String
        archivos       = List      // cada uno: nombre, ruta (obligatorios); proceso (opcional)
    }
}
