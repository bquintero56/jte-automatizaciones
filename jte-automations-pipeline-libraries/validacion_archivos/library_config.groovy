fields {
    required {
        host            = String
        user            = String
        runAsUser       = String
        formatoFecha    = String
        formatoMes      = String
        zonaHoraria     = String
        diasAtras       = Integer
        estadoSiFaltan  = String
        horaRutaGeneral = String
        rutaGeneral     = String
        archivos        = List      // cada uno: nombre, rutaEspecifica, patronEspecifico, horaEspecifica, estado (ACTIVO | PAUSADO);
                                    // opcional: proceso, modificadoMismoDia (true = en la ruta general exige modificación del día)
    }
}
