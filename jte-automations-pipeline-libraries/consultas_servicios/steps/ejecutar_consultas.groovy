// Ejecuta con curl cada consulta de config.consultas (REST o SOAP) y valida el código HTTP contra codigosOk.
// Las credenciales salen de Jenkins (withCredentials); nunca de la config.
// Devuelve [fecha, resultados]; por consulta: [nombre, metodo, url, codigo, tiempo, estatus, detalle].
Map call() {
    def fallidas   = []
    def resultados = []

    def fecha = sh(
        returnStdout: true,
        script: """#!/bin/sh
TZ='${config.zonaHoraria}' date +'%Y-%m-%d %H:%M'
"""
    ).trim()

    echo "Fecha: ${fecha} | Consultas a ejecutar: ${config.consultas.size()}"

    // Comillas simples para el shell (una ' dentro del valor se escribe como '\'')
    def q = { valor -> "'" + String.valueOf(valor).replace("'", "'\\''") + "'" }

    for (int i = 0; i < config.consultas.size(); i++) {
        def c      = config.consultas[i]
        def nombre = c.nombre ?: "Consulta ${i + 1}"
        def metodo = String.valueOf(c.metodo ?: 'GET').trim().toUpperCase()
        def auth   = String.valueOf(c.auth ?: '').trim().toLowerCase()
        def okList = (c.codigosOk ?: config.codigosOk).collect { it.toString() }

        // Proxy: el de la consulta si lo define (aunque sea '' = sin proxy); si no, el general
        def proxy = String.valueOf((c.containsKey('proxy') ? c.proxy : config.proxy) ?: '').trim()

        echo "------ ${nombre} (${metodo} ${c.url}) ------" + (proxy ? " vía proxy ${proxy}" : '')

        // Headers y cuerpo (el cuerpo va a un archivo para no romper comillas ni saltos de línea)
        def headers = ''
        if (c.headers) {
            for (def k : c.headers.keySet()) {
                headers += " -H ${q("${k}: ${c.headers[k]}")}"
            }
        }
        def datos = ''
        if (c.body) {
            writeFile file: 'consulta_cuerpo.txt', text: String.valueOf(c.body).trim()
            datos = ' --data-binary @consulta_cuerpo.txt'
        }

        // extra = autenticación y proxy; las variables se expanden en el shell, no en Groovy (el secreto no queda en el log)
        def invocar = { String extra ->
            return sh(
                returnStatus: true,
                script: """#!/bin/sh
curl -sS -X ${metodo} --max-time ${config.timeoutSegundos}${headers}${extra}${datos} \\
    -o consulta_respuesta.txt -w '%{http_code} %{time_total}' ${q(c.url)} > consulta_estado.txt 2> consulta_error.txt
"""
            )
        }

        def rc      = 0
        def detalle = ''
        try {
            def credenciales = []
            def extra        = ''

            if (auth == 'bearer') {
                credenciales << string(credentialsId: c.credencial, variable: 'AUTH_TOKEN')
                extra += ' -H "Authorization: Bearer $AUTH_TOKEN"'
            } else if (auth == 'basic') {
                credenciales << usernamePassword(credentialsId: c.credencial, usernameVariable: 'AUTH_USER', passwordVariable: 'AUTH_PASS')
                extra += ' -u "$AUTH_USER:$AUTH_PASS"'
            }

            if (proxy) {
                extra += " --proxy ${q(proxy)}"
                if (config.credencialProxy) {
                    // Proxy con usuario y clave (credencial Username with password)
                    credenciales << usernamePassword(credentialsId: config.credencialProxy, usernameVariable: 'PROXY_USER', passwordVariable: 'PROXY_PASS')
                    extra += ' --proxy-user "$PROXY_USER:$PROXY_PASS"'
                }
            }

            if (credenciales) {
                withCredentials(credenciales) {
                    rc = invocar(extra)
                }
            } else {
                rc = invocar(extra)
            }
        }
        catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            throw e     // conserva el estado ABORTED
        }
        catch (Exception e) {
            rc      = -1
            detalle = "Error al preparar la consulta: ${e.message ?: e.toString()}"   // ej. credencial inexistente
        }

        def estado = fileExists('consulta_estado.txt') ? readFile('consulta_estado.txt').trim().tokenize(' ') : []
        def codigo = (rc >= 0 && estado.size() > 0) ? estado[0] : '000'
        def tiempo = (estado.size() > 1) ? estado[1] : ''
        def ok     = (rc == 0 && okList.contains(codigo))

        if (ok) {
            echo "✅ HTTP ${codigo} en ${tiempo}s"
        } else {
            if (!detalle) {
                if (rc != 0) {
                    // curl no obtuvo respuesta (timeout, DNS, certificado, conexión rechazada...)
                    def err = fileExists('consulta_error.txt') ? readFile('consulta_error.txt').trim() : ''
                    detalle = err ? err.readLines()[0] : "curl terminó con código ${rc}"
                } else {
                    detalle = "HTTP ${codigo} (se esperaba ${okList.join(' o ')})"
                }
            }
            echo "❌ ${detalle}"

            // Primeros caracteres de la respuesta, para diagnosticar
            def resp = fileExists('consulta_respuesta.txt') ? readFile('consulta_respuesta.txt').trim() : ''
            if (resp) {
                echo "Respuesta: ${resp.length() > 500 ? resp.substring(0, 500) + '...' : resp}"
            }
            fallidas << "${nombre}: ${detalle}"
        }

        resultados << [
            nombre:  nombre,
            metodo:  metodo,
            url:     c.url,
            codigo:  codigo,
            tiempo:  tiempo,
            estatus: ok ? 'OK' : 'ERROR',
            detalle: ok ? '' : detalle
        ]

        sh 'rm -f consulta_cuerpo.txt consulta_respuesta.txt consulta_estado.txt consulta_error.txt'
    }

    // Resumen
    echo "=========== RESUMEN ==========="
    echo "OK:      ${resultados.size() - fallidas.size()} de ${resultados.size()}"
    echo "Fallidas: ${fallidas.size()} de ${resultados.size()}"

    if (fallidas) {
        echo "Con error:\n - ${fallidas.join('\n - ')}"

        // No se usa error(): el pipeline debe seguir hasta enviar el correo
        switch (config.estadoSiFalla) {
            case 'FAILURE':
                echo "Fallaron ${fallidas.size()} consulta(s): el build se marca como FAILURE"
                currentBuild.result = 'FAILURE'
                break
            case 'UNSTABLE':
                unstable("Fallaron ${fallidas.size()} consulta(s)")
                break
            default:
                break
        }
    } else {
        echo "Todas las consultas respondieron OK"
    }

    return [fecha: fecha, resultados: resultados]
}
