package com.example.perfilusuario

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiCliente {

    companion object {

        // Dirección del backend durante las pruebas
        private const val SERVIDOR =
            "http://127.0.0.1:5000"

        private const val TIEMPO_ESPERA = 10000
    }

    // Realizar solicitudes HTTP al servidor
    private fun ejecutarSolicitud(
        ruta: String,
        metodo: String,
        contenido: JSONObject? = null,
        token: String? = null
    ): JSONObject {

        val direccion = URL("$SERVIDOR$ruta")

        val conexion = direccion.openConnection()
                as HttpURLConnection

        try {

            conexion.requestMethod = metodo
            conexion.connectTimeout = TIEMPO_ESPERA
            conexion.readTimeout = TIEMPO_ESPERA

            conexion.setRequestProperty(
                "Accept",
                "application/json"
            )

            if (!token.isNullOrBlank()) {
                conexion.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
            }

            if (contenido != null) {

                conexion.doOutput = true

                conexion.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )

                conexion.outputStream.use { salida ->
                    salida.write(
                        contenido.toString().toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            }

            val codigo = conexion.responseCode

            val flujo = if (codigo in 200..299) {
                conexion.inputStream
            } else {
                conexion.errorStream
            }

            val respuesta = flujo?.bufferedReader(
                Charsets.UTF_8
            )?.use { it.readText() }.orEmpty()

            val resultado = if (respuesta.isNotBlank()) {
                JSONObject(respuesta)
            } else {
                JSONObject()
            }

            resultado.put("codigo_http", codigo)

            return resultado

        } finally {
            conexion.disconnect()
        }
    }

    // Enviar los datos de un nuevo usuario
    fun registrar(
        nombre: String,
        correo: String,
        contrasena: String
    ): JSONObject {

        val datos = JSONObject().apply {
            put("nombre", nombre)
            put("correo", correo)
            put("contrasena", contrasena)
        }

        return ejecutarSolicitud(
            "/api/registro",
            "POST",
            datos
        )
    }

    // Comprobar las credenciales del usuario
    fun autenticar(
        correo: String,
        contrasena: String
    ): JSONObject {

        val credenciales = JSONObject().apply {
            put("correo", correo)
            put("contrasena", contrasena)
        }

        return ejecutarSolicitud(
            "/api/login",
            "POST",
            credenciales
        )
    }

    // Obtener información del perfil autenticado
    fun consultarPerfil(
        token: String
    ): JSONObject {

        return ejecutarSolicitud(
            "/api/perfil",
            "GET",
            token = token
        )
    }
}
