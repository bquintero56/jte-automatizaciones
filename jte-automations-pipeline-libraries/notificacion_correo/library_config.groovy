fields {
    required {
        destinatarios = String
        asuntoPrefijo = String
        asuntoSufijo  = String
    }
    optional {
        copia         = String      // CC separados por coma; vacío o ausente = sin copia
    }
}
