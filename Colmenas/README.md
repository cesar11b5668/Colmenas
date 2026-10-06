# Colmenas 1.1.2
Aplicación Android offline para gestión apícola.

## Incluido
- Lista de apiarios: abre uno para ver y agregar sus colmenas. Las colmenas nuevas se asignan automáticamente al apiario abierto.
- Identificador único y foto de identificación por colmena; tarjetas NFC asociadas a los apiarios.
- Historial de inspecciones: salud (Buena, Regular, Muerta), color de miel (Clara, Oscura), fuerza de 1 (bajo) a 5 (fuerte), descripción y hasta seis fotos.
- QR para abrir una colmena; NFC para abrir el apiario y ver todas sus colmenas con la app abierta.
- Room/SQLite y fotos guardadas en el teléfono: no requiere Internet.
- Migración de la base de datos anterior sin borrar apiarios, colmenas ni inspecciones. Los valores históricos de miel se conservan como registros anteriores.

## Compilar y validar
Abrir el proyecto con Android Studio y JDK 17. Desde esta carpeta:

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. Las pruebas cubren la interpretación de NFC y la migración de datos, incluida la persistencia de varias fotos y la unicidad de las tarjetas.

## Asociar una tarjeta NFC
1. Activa NFC en el teléfono y abre la app.
2. Pulsa **+ Apiario** y después **Asociar tarjeta NFC**. Para un apiario existente, abre su ficha y pulsa **Agregar tarjeta NFC**.
3. Acerca la tarjeta a la parte trasera del teléfono. Para un apiario nuevo, pulsa **Guardar** después de que aparezca **Tarjeta NFC agregada**.
4. En visitas posteriores, acerca la tarjeta con la app abierta: se abrirá el apiario con su lista de colmenas.

La asociación utiliza el identificador de la tarjeta; no escribe ni modifica su contenido y también admite tarjetas vacías. Una tarjeta solo puede pertenecer a un apiario, y un apiario puede tener varias tarjetas. Puedes quitar tarjetas desde la ficha del apiario. Las tarjetas asociadas a colmenas en versiones anteriores se trasladan automáticamente a sus apiarios, sin borrar registros ni fotos. Las tarjetas NDEF anteriores abren el apiario correspondiente; los QR siguen abriendo la colmena individual.

## Fotos
Usa **Agregar foto** en una colmena o **Agregar fotos** en la descripción de una inspección. Selecciona las imágenes con el selector de archivos del teléfono. Se copian al almacenamiento privado de la app para conservarlas aunque el permiso temporal del selector termine. Las fotos son opcionales; una inspección admite hasta seis. Se muestran en la ficha y en el historial.

La lectura física de NFC, el selector de fotos y la instalación/actualización del APK deben verificarse en un teléfono Android. Para actualizar conservando los datos, instala un APK con la misma firma que la versión anterior; no desinstales la app.
