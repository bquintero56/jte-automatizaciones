fields {
    required {
        host            = String
        user            = String
        runAsUser       = String
        formatoFecha    = String
        zonaHoraria     = String
        diasAtras       = Integer
        estadoSiFaltan  = String
        horaRutaGeneral = String
        ventanaMinutos  = Integer
        rutaGeneral     = String
        archivos        = List      // cada uno: nombre, rutaEspecifica, patronEspecifico, horaEspecifica, estado (ACTIVO | PAUSADO)
    }
}
