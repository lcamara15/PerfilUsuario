package com.example.perfilusuario

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SesionSegura(contexto: Context) {

    private val preferencias = contexto.getSharedPreferences(
        "sesion_privada",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val ALIAS_CLAVE = "perfil_usuario_aes"
        private const val CAMPO_TOKEN = "token_cifrado"
        private const val CAMPO_IV = "vector_inicial"
    }

    // Obtener o generar la clave del dispositivo
    private fun obtenerClave(): SecretKey {

        val almacen = KeyStore.getInstance("AndroidKeyStore")
        almacen.load(null)

        val existente = almacen.getKey(
            ALIAS_CLAVE,
            null
        ) as? SecretKey

        if (existente != null) {
            return existente
        }

        val generador = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val especificacion = KeyGenParameterSpec.Builder(
            ALIAS_CLAVE,
            KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .build()

        generador.init(especificacion)

        return generador.generateKey()
    }

    // Proteger y conservar el token
    fun guardarToken(token: String): Boolean {

        if (token.isBlank()) return false

        return try {

            val cifrador = Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

            cifrador.init(
                Cipher.ENCRYPT_MODE,
                obtenerClave()
            )

            val datosCifrados = cifrador.doFinal(
                token.toByteArray(Charsets.UTF_8)
            )

            val contenido = Base64.encodeToString(
                datosCifrados,
                Base64.NO_WRAP
            )

            val vector = Base64.encodeToString(
                cifrador.iv,
                Base64.NO_WRAP
            )

            preferencias.edit()
                .putString(CAMPO_TOKEN, contenido)
                .putString(CAMPO_IV, vector)
                .commit()

        } catch (error: Exception) {

            eliminarSesion()
            false
        }
    }

    // Recuperar el token almacenado
    fun leerToken(): String? {

        val contenido = preferencias.getString(
            CAMPO_TOKEN,
            null
        ) ?: return null

        val vector = preferencias.getString(
            CAMPO_IV,
            null
        ) ?: return null

        return try {

            val cifrador = Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

            val iv = Base64.decode(
                vector,
                Base64.NO_WRAP
            )

            cifrador.init(
                Cipher.DECRYPT_MODE,
                obtenerClave(),
                GCMParameterSpec(128, iv)
            )

            val resultado = cifrador.doFinal(
                Base64.decode(
                    contenido,
                    Base64.NO_WRAP
                )
            )

            String(resultado, Charsets.UTF_8)

        } catch (error: Exception) {

            eliminarSesion()
            null
        }
    }

    // Descartar las credenciales almacenadas
    fun eliminarSesion() {

        preferencias.edit()
            .remove(CAMPO_TOKEN)
            .remove(CAMPO_IV)
            .commit()
    }
}
