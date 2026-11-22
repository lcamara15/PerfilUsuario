package com.example.perfilusuario

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.AtomicFile
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.concurrent.Executors

class PerfilActivity : AppCompatActivity() {

    private lateinit var nombreUsuario: TextView
    private lateinit var correoUsuario: TextView
    private lateinit var imagenPerfil: ImageView

    private lateinit var botonCambiarFoto: Button
    private lateinit var botonSalir: Button
    private lateinit var opcionCamara: TextView
    private lateinit var opcionGaleria: TextView

    private lateinit var sesion: SesionSegura

    private val clienteApi = ApiCliente()
    private val ejecutor = Executors.newSingleThreadExecutor()

    private var tokenSesion = ""
    private var identificadorUsuario = -1
    private var archivoTemporal: File? = null
    private var procesandoImagen = false

    // Recibir el resultado de la cámara
    private val capturarFoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { completada ->

        val archivo = archivoTemporal

        if (completada && archivo != null && archivo.exists()) {

            guardarFotografia(
                Uri.fromFile(archivo),
                archivo
            )

        } else {

            archivo?.delete()
            archivoTemporal = null

            if (completada) {
                mostrarMensaje(
                    "No fue posible recuperar la fotografía."
                )
            }
        }
    }

    // Recibir una fotografía seleccionada de la galería
    private val seleccionarFoto = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { direccion ->

        if (direccion != null) {
            guardarFotografia(direccion)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        sesion = SesionSegura(this)

        // Recuperar una captura pendiente si Android recrea la pantalla
        val rutaGuardada = savedInstanceState?.getString(
            "RUTA_CAPTURA"
        )

        if (!rutaGuardada.isNullOrBlank()) {
            archivoTemporal = File(rutaGuardada)
        }

        adaptarPantalla()
        inicializarVista()
        configurarAcciones()
        recuperarSesion()
    }

    // Ajustar el contenido a las barras del dispositivo
    private fun adaptarPantalla() {

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.contenedorPerfil)
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

    // Asociar los elementos del diseño
    private fun inicializarVista() {

        nombreUsuario = findViewById(R.id.tvNombrePerfil)
        correoUsuario = findViewById(R.id.tvCorreoPerfil)
        imagenPerfil = findViewById(R.id.ivFotoPerfil)

        botonCambiarFoto = findViewById(R.id.btnCambiarFoto)
        botonSalir = findViewById(R.id.btnCerrarSesion)

        opcionCamara = findViewById(R.id.tvAbrirCamara)
        opcionGaleria = findViewById(R.id.tvAbrirGaleria)

        nombreUsuario.text = "Cargando perfil..."
        correoUsuario.text = ""

        // Usar el fondo ovalado como límite del avatar
        imagenPerfil.outlineProvider =
            ViewOutlineProvider.BACKGROUND

        imagenPerfil.clipToOutline = true

        habilitarFotografias(false)
    }

    // Recuperar el token de sesión almacenado
    private fun recuperarSesion() {

        tokenSesion = sesion.leerToken().orEmpty()

        if (tokenSesion.isBlank()) {

            mostrarMensaje(
                "Inicia sesión para acceder a tu perfil."
            )

            finalizarSesion()
            return
        }

        cargarInformacion()
    }

    // Consultar los datos del usuario autenticado
    private fun cargarInformacion() {

        ejecutor.execute {

            try {

                val respuesta = clienteApi.consultarPerfil(
                    tokenSesion
                )

                val codigo = respuesta.optInt("codigo_http")
                val usuario = respuesta.optJSONObject("usuario")

                runOnUiThread {

                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    when {

                        codigo == 200 && usuario != null -> {

                            val id = usuario.optInt("id", -1)
                            val nombre = usuario.optString(
                                "nombre",
                                ""
                            )
                            val correo = usuario.optString(
                                "correo",
                                ""
                            )

                            if (
                                id > 0 &&
                                nombre.isNotBlank() &&
                                correo.isNotBlank()
                            ) {

                                identificadorUsuario = id

                                nombreUsuario.text = nombre
                                correoUsuario.text = correo

                                habilitarFotografias(true)
                                recuperarFotografia()

                            } else {

                                mostrarErrorPerfil()
                            }
                        }

                        codigo == 401 -> {

                            mostrarMensaje(
                                "Tu sesión ha expirado. " +
                                        "Inicia sesión nuevamente."
                            )

                            finalizarSesion()
                        }

                        else -> {
                            mostrarErrorPerfil()
                        }
                    }
                }

            } catch (error: Exception) {

                runOnUiThread {

                    if (!isFinishing && !isDestroyed) {
                        mostrarErrorPerfil()
                    }
                }
            }
        }
    }

    // Configurar las acciones disponibles
    private fun configurarAcciones() {

        botonCambiarFoto.setOnClickListener {
            mostrarOpcionesFoto()
        }

        opcionCamara.setOnClickListener {
            abrirCamara()
        }

        opcionGaleria.setOnClickListener {
            abrirGaleria()
        }

        botonSalir.setOnClickListener {
            confirmarSalida()
        }
    }

    // Mostrar las alternativas para actualizar la fotografía
    private fun mostrarOpcionesFoto() {

        if (identificadorUsuario <= 0 || procesandoImagen) {
            return
        }

        val opciones = arrayOf(
            "Tomar fotografía",
            "Elegir de galería"
        )

        AlertDialog.Builder(this)
            .setTitle("Actualizar fotografía")
            .setItems(opciones) { _, seleccion ->

                when (seleccion) {

                    0 -> abrirCamara()

                    1 -> abrirGaleria()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Preparar un archivo temporal y abrir la cámara
    private fun abrirCamara() {

        if (identificadorUsuario <= 0 || procesandoImagen) {
            return
        }

        try {

            val carpeta = File(cacheDir, "capturas")

            if (!carpeta.exists() && !carpeta.mkdirs()) {
                throw IllegalStateException(
                    "No se pudo preparar la carpeta temporal."
                )
            }

            val archivo = File.createTempFile(
                "captura_",
                ".jpg",
                carpeta
            )

            archivoTemporal = archivo

            val direccion = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                archivo
            )

            capturarFoto.launch(direccion)

        } catch (error: Exception) {

            archivoTemporal?.delete()
            archivoTemporal = null

            mostrarMensaje(
                "No fue posible abrir la cámara."
            )
        }
    }

    // Abrir el selector de imágenes de Android
    private fun abrirGaleria() {

        if (identificadorUsuario <= 0 || procesandoImagen) {
            return
        }

        try {

            seleccionarFoto.launch("image/*")

        } catch (error: Exception) {

            mostrarMensaje(
                "No fue posible abrir la galería."
            )
        }
    }

    // Procesar y guardar una fotografía de forma segura
    private fun guardarFotografia(
        direccion: Uri,
        capturaTemporal: File? = null
    ) {

        val id = identificadorUsuario

        if (id <= 0 || procesandoImagen) return

        procesandoImagen = true
        habilitarFotografias(false)

        ejecutor.execute {

            var guardado = false

            try {

                val imagen = prepararImagen(direccion)

                if (imagen != null) {

                    try {

                        val carpeta = File(
                            filesDir,
                            "perfiles"
                        )

                        if (
                            !carpeta.exists() &&
                            !carpeta.mkdirs()
                        ) {
                            throw IllegalStateException(
                                "No se pudo crear la carpeta de perfiles."
                            )
                        }

                        // Cada usuario tiene un archivo independiente
                        val destino = File(
                            carpeta,
                            "perfil_$id.jpg"
                        )

                        val archivoSeguro = AtomicFile(destino)

                        // AtomicFile es lo que conserva una copia recuperable
                        // mientras se escribe la nueva imagen
                        val salida = archivoSeguro.startWrite()

                        try {

                            val comprimida = imagen.compress(
                                Bitmap.CompressFormat.JPEG,
                                85,
                                salida
                            )

                            if (!comprimida) {
                                throw IllegalStateException(
                                    "No se pudo comprimir la fotografía."
                                )
                            }

                            archivoSeguro.finishWrite(salida)
                            guardado = true

                        } catch (error: Exception) {

                            archivoSeguro.failWrite(salida)
                            throw error
                        }

                    } finally {

                        imagen.recycle()
                    }
                }

            } catch (error: Exception) {

                guardado = false

            } finally {

                // El archivo original temporal ya no se necesita
                capturaTemporal?.delete()

                if (capturaTemporal != null) {
                    archivoTemporal = null
                }
            }

            runOnUiThread {

                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                procesandoImagen = false
                habilitarFotografias(true)

                if (guardado) {

                    recuperarFotografia()

                    mostrarMensaje(
                        "Tu fotografía se actualizó correctamente."
                    )

                } else {

                    mostrarMensaje(
                        "No fue posible guardar la fotografía. " +
                                "Se conservará la imagen anterior."
                    )
                }
            }
        }
    }

    // Reducir el tamaño y corregir la orientación
    private fun prepararImagen(direccion: Uri): Bitmap? {

        val dimensiones = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        contentResolver.openInputStream(direccion)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                dimensiones
            )
        }

        if (
            dimensiones.outWidth <= 0 ||
            dimensiones.outHeight <= 0
        ) {
            return null
        }

        // Reducir el consumo de memoria al cargar la imagen
        var escala = 1

        while (
            dimensiones.outWidth / escala > 1024 ||
            dimensiones.outHeight / escala > 1024
        ) {
            escala *= 2
        }

        val opciones = BitmapFactory.Options().apply {
            inSampleSize = escala
        }

        val imagen = contentResolver.openInputStream(
            direccion
        )?.use {

            BitmapFactory.decodeStream(
                it,
                null,
                opciones
            )

        } ?: return null

        val orientacion = try {

            contentResolver.openInputStream(direccion)?.use {

                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

            } ?: ExifInterface.ORIENTATION_NORMAL

        } catch (error: Exception) {

            ExifInterface.ORIENTATION_NORMAL
        }

        val matriz = Matrix()

        when (orientacion) {

            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matriz.postRotate(90f)
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matriz.postRotate(180f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matriz.postRotate(270f)
            }

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matriz.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matriz.postScale(1f, -1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matriz.postRotate(90f)
                matriz.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matriz.postRotate(270f)
                matriz.postScale(-1f, 1f)
            }
        }

        if (matriz.isIdentity) {
            return imagen
        }

        val corregida = try {

            Bitmap.createBitmap(
                imagen,
                0,
                0,
                imagen.width,
                imagen.height,
                matriz,
                true
            )

        } catch (error: Exception) {

            imagen.recycle()
            return null
        }

        if (corregida !== imagen) {
            imagen.recycle()
        }

        return corregida
    }

    // Recuperar la fotografía de la cuenta actual
    private fun recuperarFotografia() {

        if (identificadorUsuario <= 0) return

        val destino = File(
            File(filesDir, "perfiles"),
            "perfil_$identificadorUsuario.jpg"
        )

        val archivoSeguro = AtomicFile(destino)

        val imagen = try {

            archivoSeguro.openRead().use { entrada ->

                BitmapFactory.decodeStream(entrada)
            }

        } catch (error: Exception) {

            null
        }

        if (imagen != null) {

            // Retirar el tinte del icono predeterminado
            imagenPerfil.imageTintList = null

            imagenPerfil.setPadding(0, 0, 0, 0)

            imagenPerfil.scaleType =
                ImageView.ScaleType.CENTER_CROP

            imagenPerfil.setImageBitmap(imagen)
        }
    }

    // Habilitar o bloquear temporalmente las opciones
    private fun habilitarFotografias(habilitado: Boolean) {

        botonCambiarFoto.isEnabled = habilitado
        opcionCamara.isEnabled = habilitado
        opcionGaleria.isEnabled = habilitado
    }

    // Solicitar confirmación antes de cerrar sesión
    private fun confirmarSalida() {

        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Deseas salir de tu cuenta?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ ->

                finalizarSesion()
            }
            .show()
    }

    // Eliminar la sesión sin borrar las fotografías
    private fun finalizarSesion() {

        sesion.eliminarSesion()

        tokenSesion = ""
        identificadorUsuario = -1

        val regreso = Intent(
            this,
            MainActivity::class.java
        ).apply {

            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(regreso)
        finish()
    }

    // Informar cuando no es posible consultar Flask
    private fun mostrarErrorPerfil() {

        nombreUsuario.text = "Perfil no disponible"
        correoUsuario.text = ""

        habilitarFotografias(false)

        mostrarMensaje(
            "No fue posible cargar tus datos. " +
                    "Comprueba la conexión con el servidor."
        )
    }

    // Mostrar avisos breves
    private fun mostrarMensaje(texto: String) {

        Toast.makeText(
            this,
            texto,
            Toast.LENGTH_LONG
        ).show()
    }

    // Conservar la ruta si Android recrea la actividad
    override fun onSaveInstanceState(outState: Bundle) {

        archivoTemporal?.let { archivo ->

            outState.putString(
                "RUTA_CAPTURA",
                archivo.absolutePath
            )
        }

        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {

        ejecutor.shutdownNow()
        super.onDestroy()
    }
}
