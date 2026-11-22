package com.example.perfilusuario

import android.app.AlertDialog
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors

class RegistroActivity : AppCompatActivity() {

    private lateinit var campoNombre: EditText
    private lateinit var campoCorreo: EditText
    private lateinit var campoClave: EditText
    private lateinit var campoConfirmacion: EditText

    private lateinit var textoAviso: TextView
    private lateinit var botonRegistro: Button
    private lateinit var enlaceAcceso: TextView

    private val api = ApiCliente()
    private val ejecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        adaptarVentana()
        vincularComponentes()
        configurarInteracciones()
    }

    // Respetar las barras del sistema
    private fun adaptarVentana() {

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.contenedorRegistro)
        ) { vista, insets ->

            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            vista.setPadding(
                barras.left,
                barras.top,
                barras.right,
                barras.bottom
            )

            insets
        }
    }

    // Relacionar el diseño con la actividad
    private fun vincularComponentes() {

        campoNombre = findViewById(R.id.etNombreRegistro)
        campoCorreo = findViewById(R.id.etCorreoRegistro)
        campoClave = findViewById(R.id.etClaveRegistro)
        campoConfirmacion = findViewById(R.id.etConfirmarClave)

        textoAviso = findViewById(R.id.tvAvisoRegistro)
        botonRegistro = findViewById(R.id.btnCrearCuenta)
        enlaceAcceso = findViewById(R.id.tvVolverLogin)
    }

    // Asignar las acciones de los controles
    private fun configurarInteracciones() {

        botonRegistro.setOnClickListener {
            revisarRegistro()
        }

        enlaceAcceso.setOnClickListener {
            finish()
        }
    }

    // Comprobar los datos antes de enviarlos
    private fun revisarRegistro() {

        val nombre = campoNombre.text.toString().trim()
        val correo = campoCorreo.text.toString().trim()
        val clave = campoClave.text.toString()
        val confirmacion = campoConfirmacion.text.toString()

        limpiarIndicaciones()

        when {

            nombre.isBlank() -> {
                indicarError(
                    campoNombre,
                    "Ingresa tu nombre para continuar."
                )
            }

            nombre.length > 100 -> {
                indicarError(
                    campoNombre,
                    "El nombre excede la longitud permitida."
                )
            }

            correo.isBlank() -> {
                indicarError(
                    campoCorreo,
                    "Escribe tu correo electrónico."
                )
            }

            correo.length > 254 ||
                    !Patterns.EMAIL_ADDRESS.matcher(correo).matches() -> {
                indicarError(
                    campoCorreo,
                    "Verifica que el correo esté escrito correctamente."
                )
            }

            clave.isEmpty() -> {
                indicarError(
                    campoClave,
                    "Crea una contraseña para tu cuenta."
                )
            }

            clave.length < 8 -> {
                indicarError(
                    campoClave,
                    "La contraseña debe tener al menos 8 caracteres."
                )
            }

            clave.length > 128 -> {
                indicarError(
                    campoClave,
                    "La contraseña excede la longitud permitida."
                )
            }

            confirmacion.isEmpty() -> {
                indicarError(
                    campoConfirmacion,
                    "Confirma la contraseña que elegiste."
                )
            }

            clave != confirmacion -> {
                indicarError(
                    campoConfirmacion,
                    "Las contraseñas no coinciden. Verifícalas."
                )
            }

            else -> {
                enviarRegistro(nombre, correo, clave)
            }
        }
    }

    // Registrar el usuario mediante la API
    private fun enviarRegistro(
        nombre: String,
        correo: String,
        clave: String
    ) {

        cambiarEstadoRegistro(true)

        ejecutor.execute {

            try {

                val respuesta = api.registrar(
                    nombre,
                    correo,
                    clave
                )

                val codigo = respuesta.optInt("codigo_http")
                val detalle = respuesta.optString(
                    "error",
                    "No fue posible completar el registro."
                )

                runOnUiThread {

                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    cambiarEstadoRegistro(false)

                    when (codigo) {

                        201 -> {
                            confirmarRegistro()
                        }

                        409 -> {
                            indicarError(
                                campoCorreo,
                                "Este correo ya pertenece a una cuenta."
                            )
                        }

                        400 -> {
                            mostrarAviso(detalle)
                        }

                        else -> {
                            mostrarAviso(
                                "No se pudo crear la cuenta. Inténtalo nuevamente."
                            )
                        }
                    }
                }

            } catch (error: Exception) {

                runOnUiThread {

                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    cambiarEstadoRegistro(false)

                    mostrarAviso(
                        "No se pudo conectar con el servidor. Revisa la conexión."
                    )
                }
            }
        }
    }

    // Informar que la cuenta fue creada
    private fun confirmarRegistro() {

        AlertDialog.Builder(this)
            .setTitle("Cuenta creada")
            .setMessage(
                "Tu registro se completó correctamente. " +
                        "Ahora puedes iniciar sesión."
            )
            .setPositiveButton("Continuar") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    // Controlar el botón durante la solicitud
    private fun cambiarEstadoRegistro(procesando: Boolean) {

        botonRegistro.isEnabled = !procesando
        enlaceAcceso.isEnabled = !procesando

        botonRegistro.text = if (procesando) {
            "Creando cuenta..."
        } else {
            "Crear cuenta"
        }
    }

    // Señalar un campo que necesita corrección
    private fun indicarError(
        campo: EditText,
        descripcion: String
    ) {

        campo.error = descripcion
        campo.requestFocus()

        mostrarAviso(descripcion)
    }

    // Presentar información dentro del formulario
    private fun mostrarAviso(contenido: String) {

        textoAviso.text = contenido
        textoAviso.visibility = View.VISIBLE
    }

    // Retirar las indicaciones anteriores
    private fun limpiarIndicaciones() {

        textoAviso.text = ""
        textoAviso.visibility = View.GONE

        campoNombre.error = null
        campoCorreo.error = null
        campoClave.error = null
        campoConfirmacion.error = null
    }

    override fun onDestroy() {
        ejecutor.shutdownNow()
        super.onDestroy()
    }
}
