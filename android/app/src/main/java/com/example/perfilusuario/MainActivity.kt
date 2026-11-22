package com.example.perfilusuario

import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var entradaCorreo: EditText
    private lateinit var entradaClave: EditText
    private lateinit var avisoFormulario: TextView
    private lateinit var controlClave: TextView
    private lateinit var accesoPrincipal: Button
    private lateinit var accesoRegistro: TextView

    private val api = ApiCliente()
    private val ejecutor = Executors.newSingleThreadExecutor()

    private lateinit var sesion: SesionSegura

    private var mostrarContrasena = false
    private var procesandoAcceso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sesion = SesionSegura(this)

        ajustarContenido()
        inicializarElementos()
        establecerAcciones()
        verificarSesionGuardada()
    }

    // Ajustar el contenido a las barras del sistema
    private fun ajustarContenido() {

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { vista, ajustes ->

            val espacios = ajustes.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            vista.setPadding(
                espacios.left,
                espacios.top,
                espacios.right,
                espacios.bottom
            )

            ajustes
        }
    }

    // Asociar los controles del formulario
    private fun inicializarElementos() {

        entradaCorreo = findViewById(R.id.etCorreo)
        entradaClave = findViewById(R.id.etContrasena)

        avisoFormulario = findViewById(R.id.tvMensaje)
        controlClave = findViewById(R.id.tvMostrarClave)

        accesoPrincipal = findViewById(R.id.btnIngresar)
        accesoRegistro = findViewById(R.id.tvRegistro)
    }

    // Configurar las interacciones
    private fun establecerAcciones() {

        controlClave.setOnClickListener {
            alternarContrasena()
        }

        accesoPrincipal.setOnClickListener {
            comprobarCredenciales()
        }

        accesoRegistro.setOnClickListener {

            val pantallaRegistro = Intent(
                this,
                RegistroActivity::class.java
            )

            startActivity(pantallaRegistro)
        }
    }

    // Recuperar una sesión existente
    private fun verificarSesionGuardada() {

        val tokenGuardado = sesion.leerToken()

        if (!tokenGuardado.isNullOrBlank()) {
            abrirPerfil()
        }
    }

    // Mostrar u ocultar la contraseña
    private fun alternarContrasena() {

        mostrarContrasena = !mostrarContrasena

        val cursorActual = entradaClave.selectionStart

        entradaClave.transformationMethod =
            if (mostrarContrasena) {
                null
            } else {
                PasswordTransformationMethod.getInstance()
            }

        controlClave.text =
            if (mostrarContrasena) "Ocultar" else "Ver"

        controlClave.contentDescription =
            if (mostrarContrasena) {
                "Ocultar contraseña"
            } else {
                "Mostrar contraseña"
            }

        entradaClave.setSelection(
            cursorActual.coerceIn(0, entradaClave.length())
        )
    }

    // Validar los datos antes de consultar el servidor
    private fun comprobarCredenciales() {

        if (procesandoAcceso) return

        val email = entradaCorreo.text.toString().trim()
        val clave = entradaClave.text.toString()

        ocultarAviso()

        entradaCorreo.error = null
        entradaClave.error = null

        when {

            email.isBlank() -> {
                indicarError(
                    entradaCorreo,
                    "Escribe tu correo para continuar."
                )
            }

            email.length > 254 ||
                    !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {

                indicarError(
                    entradaCorreo,
                    "Revisa el formato de tu correo electrónico."
                )
            }

            clave.isEmpty() -> {
                indicarError(
                    entradaClave,
                    "Introduce tu contraseña para acceder."
                )
            }

            else -> {
                solicitarAcceso(email, clave)
            }
        }
    }

    // Comprobar las credenciales mediante Flask
    private fun solicitarAcceso(
        email: String,
        clave: String
    ) {

        actualizarBoton(true)

        ejecutor.execute {

            try {

                val respuesta = api.autenticar(email, clave)

                val codigo = respuesta.optInt("codigo_http")
                val token = respuesta.optString("token", "")

                runOnUiThread {

                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    actualizarBoton(false)

                    when {

                        codigo == 200 && token.isNotBlank() -> {
                            accesoAutorizado(token)
                        }

                        codigo == 401 -> {
                            presentarAviso(
                                "El correo o la contraseña no son correctos."
                            )
                        }

                        else -> {
                            presentarAviso(
                                respuesta.optString(
                                    "error",
                                    "No fue posible iniciar sesión."
                                )
                            )
                        }
                    }
                }

            } catch (error: Exception) {

                runOnUiThread {

                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    actualizarBoton(false)

                    presentarAviso(
                        "No se pudo establecer conexión con el servidor. " +
                                "Comprueba la conexión e inténtalo nuevamente."
                    )
                }
            }
        }
    }

    // Guardar la sesión después de autenticar
    private fun accesoAutorizado(token: String) {

        if (token.isBlank()) {
            presentarAviso(
                "No fue posible verificar tu sesión."
            )
            return
        }

        val guardado = sesion.guardarToken(token)

        if (!guardado) {
            presentarAviso(
                "No fue posible guardar tu sesión. Inténtalo nuevamente."
            )
            return
        }

        entradaClave.text.clear()
        abrirPerfil()
    }

    // Abrir la pantalla personal
    private fun abrirPerfil() {

        val pantallaPerfil = Intent(
            this,
            PerfilActivity::class.java
        ).apply {

            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(pantallaPerfil)
        finish()
    }

    // Evitar solicitudes duplicadas
    private fun actualizarBoton(cargando: Boolean) {

        procesandoAcceso = cargando

        accesoPrincipal.isEnabled = !cargando
        accesoRegistro.isEnabled = !cargando

        accesoPrincipal.text =
            if (cargando) {
                "Verificando acceso..."
            } else {
                "Iniciar sesión"
            }
    }

    // Señalar los datos que requieren corrección
    private fun indicarError(
        campo: EditText,
        descripcion: String
    ) {

        campo.error = descripcion
        campo.requestFocus()

        presentarAviso(descripcion)
    }

    // Mostrar una indicación en el formulario
    private fun presentarAviso(texto: String) {

        avisoFormulario.setTextColor(
            ContextCompat.getColor(
                this,
                R.color.color_error
            )
        )

        avisoFormulario.text = texto
        avisoFormulario.visibility = View.VISIBLE
    }

    // Limpiar mensajes anteriores
    private fun ocultarAviso() {

        avisoFormulario.text = ""
        avisoFormulario.visibility = View.GONE
    }

    override fun onDestroy() {

        ejecutor.shutdownNow()
        super.onDestroy()
    }
}
