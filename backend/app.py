import os
import re
import sqlite3
import secrets
from pathlib import Path
from datetime import datetime, timedelta, timezone
from functools import wraps

import jwt
from flask import Flask, request, jsonify, g
from werkzeug.security import generate_password_hash, check_password_hash


# Configuración general del proyecto
CARPETA_PROYECTO = Path(__file__).resolve().parent

RUTA_BD = CARPETA_PROYECTO / "usuarios.db"
RUTA_CLAVE_JWT = CARPETA_PROYECTO / "jwt_secret.key"


# Obtener una clave JWT privada y persistente
def obtener_clave_jwt():

    # Priorizar una clave definida en el entorno
    clave_entorno = os.environ.get("JWT_SECRET", "").strip()

    if clave_entorno:
        return clave_entorno

    # Recuperar la clave privada si ya existe
    if RUTA_CLAVE_JWT.exists():

        clave_guardada = RUTA_CLAVE_JWT.read_text(
            encoding="utf-8"
        ).strip()

        if not clave_guardada:
            raise RuntimeError(
                "El archivo jwt_secret.key está vacío."
            )

        return clave_guardada

    # Crear una clave aleatoria una sola vez
    clave_nueva = secrets.token_hex(32)

    try:

        with RUTA_CLAVE_JWT.open(
            "x",
            encoding="utf-8"
        ) as archivo:

            archivo.write(clave_nueva)

        return clave_nueva

    except FileExistsError:

        # Recuperar la clave si otro proceso la creó
        clave_guardada = RUTA_CLAVE_JWT.read_text(
            encoding="utf-8"
        ).strip()

        if not clave_guardada:
            raise RuntimeError(
                "No se pudo recuperar una clave JWT válida."
            )

        return clave_guardada


def crear_aplicacion(configuracion=None):

    aplicacion = Flask(__name__)

    aplicacion.config.update(
        DATABASE=str(RUTA_BD),
        SECRET_KEY=obtener_clave_jwt(),
        DURACION_TOKEN=8,
        JSON_AS_ASCII=False
    )

    if configuracion:
        aplicacion.config.update(configuracion)

    if not aplicacion.config["SECRET_KEY"]:
        raise RuntimeError(
            "No fue posible configurar la clave JWT."
        )

    # Conexión a SQLite
    def conectar_bd():

        if "conexion" not in g:

            g.conexion = sqlite3.connect(
                aplicacion.config["DATABASE"]
            )

            g.conexion.row_factory = sqlite3.Row
            g.conexion.execute("PRAGMA foreign_keys = ON")

        return g.conexion

    # Cerrar la conexión al terminar una solicitud
    @aplicacion.teardown_appcontext
    def cerrar_conexion(error=None):

        conexion = g.pop("conexion", None)

        if conexion is not None:
            conexion.close()

    # Preparar la base de datos
    def preparar_base_datos():

        with sqlite3.connect(
            aplicacion.config["DATABASE"]
        ) as conexion:

            conexion.execute("""
                CREATE TABLE IF NOT EXISTS usuarios (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    correo TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    contrasena_hash TEXT NOT NULL,
                    fecha_registro TEXT NOT NULL
                        DEFAULT CURRENT_TIMESTAMP
                )
            """)

            conexion.commit()

    preparar_base_datos()

    # Validación del correo electrónico
    def validar_correo(correo):

        expresion = (
            r"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"
        )

        return (
            len(correo) <= 254
            and re.fullmatch(expresion, correo) is not None
        )

    # Protección de las rutas privadas
    def verificar_sesion(funcion):

        @wraps(funcion)
        def validar_acceso(*args, **kwargs):

            encabezado = request.headers.get(
                "Authorization", ""
            )

            if not encabezado.startswith("Bearer "):

                return jsonify({
                    "error": "Debe iniciar sesión para continuar."
                }), 401

            token = encabezado[7:].strip()

            try:

                datos_token = jwt.decode(
                    token,
                    aplicacion.config["SECRET_KEY"],
                    algorithms=["HS256"]
                )

                identificador = int(datos_token["sub"])

            except (
                jwt.InvalidTokenError,
                KeyError,
                ValueError,
                TypeError
            ):

                return jsonify({
                    "error": "La sesión no es válida o ya ha expirado."
                }), 401

            usuario = conectar_bd().execute(
                """
                SELECT id, nombre, correo
                FROM usuarios
                WHERE id = ?
                """,
                (identificador,)
            ).fetchone()

            if usuario is None:

                return jsonify({
                    "error": "No se encontró el usuario."
                }), 401

            return funcion(
                dict(usuario),
                *args,
                **kwargs
            )

        return validar_acceso

    # Comprobar el funcionamiento del backend
    @aplicacion.get("/api/salud")
    def comprobar_servidor():

        return jsonify({
            "estado": "activo",
            "mensaje": "Servidor disponible"
        }), 200

    # Registrar un nuevo usuario
    @aplicacion.post("/api/registro")
    def registrar_usuario():

        datos = request.get_json(silent=True)

        if not isinstance(datos, dict):

            return jsonify({
                "error": "Debe proporcionar los datos en formato JSON."
            }), 400

        nombre = datos.get("nombre")
        correo = datos.get("correo")
        contrasena = datos.get("contrasena")

        if not isinstance(nombre, str) or not nombre.strip():

            return jsonify({
                "error": "Debe ingresar su nombre."
            }), 400

        if not isinstance(correo, str):

            return jsonify({
                "error": "Debe ingresar un correo electrónico."
            }), 400

        nombre = nombre.strip()
        correo = correo.strip().lower()

        if len(nombre) > 100:

            return jsonify({
                "error": "El nombre supera la longitud permitida."
            }), 400

        if not validar_correo(correo):

            return jsonify({
                "error": "El correo electrónico no es válido."
            }), 400

        if (
            not isinstance(contrasena, str)
            or not 8 <= len(contrasena) <= 128
        ):

            return jsonify({
                "error": "La contraseña debe tener entre 8 y 128 caracteres."
            }), 400

        contrasena_segura = generate_password_hash(
            contrasena
        )

        try:

            conexion = conectar_bd()

            resultado = conexion.execute(
                """
                INSERT INTO usuarios (
                    nombre,
                    correo,
                    contrasena_hash
                )
                VALUES (?, ?, ?)
                """,
                (nombre, correo, contrasena_segura)
            )

            conexion.commit()

        except sqlite3.IntegrityError:

            return jsonify({
                "error": "El correo electrónico ya está registrado."
            }), 409

        return jsonify({
            "mensaje": "Usuario registrado correctamente.",
            "usuario_id": resultado.lastrowid
        }), 201

    # Autenticar las credenciales
    @aplicacion.post("/api/login")
    def iniciar_sesion():

        datos = request.get_json(silent=True)

        if not isinstance(datos, dict):

            return jsonify({
                "error": "Debe ingresar sus credenciales."
            }), 400

        correo = datos.get("correo")
        contrasena = datos.get("contrasena")

        if (
            not isinstance(correo, str)
            or not isinstance(contrasena, str)
        ):

            return jsonify({
                "error": "Correo o contraseña incorrectos."
            }), 401

        correo = correo.strip().lower()

        usuario = conectar_bd().execute(
            """
            SELECT id, nombre, correo, contrasena_hash
            FROM usuarios
            WHERE correo = ?
            """,
            (correo,)
        ).fetchone()

        if (
            usuario is None
            or not check_password_hash(
                usuario["contrasena_hash"],
                contrasena
            )
        ):

            return jsonify({
                "error": "Correo o contraseña incorrectos."
            }), 401

        expiracion = (
            datetime.now(timezone.utc)
            + timedelta(
                hours=aplicacion.config["DURACION_TOKEN"]
            )
        )

        token = jwt.encode(
            {
                "sub": str(usuario["id"]),
                "exp": expiracion
            },
            aplicacion.config["SECRET_KEY"],
            algorithm="HS256"
        )

        return jsonify({
            "mensaje": "Inicio de sesión exitoso.",
            "token": token,
            "tipo": "Bearer",
            "expira_en_horas": aplicacion.config["DURACION_TOKEN"]
        }), 200

    # Consultar el perfil del usuario
    @aplicacion.get("/api/perfil")
    @verificar_sesion
    def consultar_perfil(usuario):

        return jsonify({
            "usuario": {
                "id": usuario["id"],
                "nombre": usuario["nombre"],
                "correo": usuario["correo"]
            }
        }), 200

    return aplicacion


# Iniciar el servidor Flask
if __name__ == "__main__":

    app = crear_aplicacion()

    app.run(
        host="127.0.0.1",
        port=5000,
        debug=False
    )
