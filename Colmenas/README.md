# Colmenas 1.0
Aplicación Android offline para gestión apícola.

## Incluido
- Apiarios y colmenas
- Identificador único por colmena
- Historial de inspecciones
- Datos de reina, estado, salud, miel y observaciones
- Lectura NFC (NDEF con código de colmena)
- Escaneo QR
- Room/SQLite local: no requiere Internet

## Compilar
Abrir el proyecto con Android Studio (JDK 17) y sincronizar Gradle. Ejecutar `assembleDebug` o Build > Build APK(s).

## NFC / QR
El contenido recomendado para ambos es el código mostrado por la app, por ejemplo `COL-A1B2C3D4`. La lectura busca ese código y abre la colmena correspondiente.
