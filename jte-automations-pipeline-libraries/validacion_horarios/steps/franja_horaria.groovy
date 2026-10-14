// Decide una sola vez qué franja horaria toca en esta ejecución.
// La franja es la hora programada MÁS RECIENTE con 0 <= (referencia - hora) < ventanaMinutos: así, con tareas a las 15:00
// y a las 15:10, la ejecución de las 15:10 solo corre las de las 15:10 (no repite las de las 15:00).
// Devuelve [todos, referencia, horas]: horas = horas de la config (tal cual están escritas) que pertenecen a la franja.
Map call(String modo = '') {
    def m     = modo?.trim()
    def todos = (m?.toUpperCase() == 'TODOS')

    def horaActual = sh(
        returnStdout: true,
        script: """#!/bin/sh
TZ='${config.zonaHoraria}' date +'%H:%M'
"""
    ).trim()

    def referencia = (m && !todos) ? m : horaActual

    if (todos) {
        echo "Franja: TODOS (se ejecutan todas las tareas)"
        return [todos: true, referencia: horaActual, horas: horas_programadas()]
    }

    def aMinutos = { String hhmm ->
        def p = hhmm.split(':')
        return p[0].toInteger() * 60 + p[1].toInteger()
    }

    def ref        = aMinutos(referencia)
    def candidatas = horas_programadas().findAll { h ->
        def diff = ref - aMinutos(h)
        return (diff >= 0 && diff < config.ventanaMinutos)
    }

    if (!candidatas) {
        echo "Franja: ninguna (referencia ${referencia}, ventana de ${config.ventanaMinutos} min)"
        return [todos: false, referencia: referencia, horas: []]
    }

    def franja = candidatas.collect { aMinutos(it) }.max()
    def horas  = candidatas.findAll { aMinutos(it) == franja }

    def omitidas = candidatas - horas
    echo "Franja: ${horas[0]} (referencia ${referencia})" +
         (omitidas ? " | se omiten por pertenecer a otra ejecución: ${omitidas.join(', ')}" : '')

    return [todos: false, referencia: referencia, horas: horas]
}
