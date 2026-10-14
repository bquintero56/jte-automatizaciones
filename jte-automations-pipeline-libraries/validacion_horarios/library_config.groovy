fields {
    required {
        host           = String
        user           = String
        runAsUser      = String
        zonaHoraria    = String
        estadoSiFaltan = String
        rutaBase       = String
        formatoMes     = String
        formatoFecha   = String
        prefijoNombre  = String
        diasAtras      = Integer
        ventanaMinutos = Integer
        archivos       = List

        sinAprobado {
            hora             = String
            textoFijo        = String
            formatoFechaCsv  = String
            formatoFechaPdf  = String
            separador        = String
            columnaTipo      = String
            valorExcluir     = String
            columnaSiniestro = String
        }

        enviocc {
            COM_ID          = String
            DES             = String
            hora            = String
            textoCsv        = String
            formatoFechaCsv = String
            separador       = String
            columna         = String
            rutaPdf         = String
            formatoFechaPdf = String
            textoPdf        = String
            extensionPdf    = String
        }
    }
}
