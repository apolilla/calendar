# Calendario

Aplicación Android nativa de calendario y tareas, inspirada conceptualmente en Google Calendar pero con interfaz propia. Muestra un calendario mensual con la semana empezando en lunes. Debajo aparece la lista de tareas del día seleccionado. Las tareas pueden repetirse, tener varias notificaciones, archivos adjuntos y fotos hechas con la cámara. Todo se guarda en el dispositivo.

## Funcionalidad

- **Calendario mensual**: semanas de lunes a domingo, navegación entre meses con flechas o deslizando, y salto a cualquier mes y año desde el título. Incluye un botón «Hoy» y resalta el día seleccionado y el día actual.
- **Indicadores por tarea**: cada tarea del día dibuja un punto de su color, en orden cronológico. Si hay más de 5 tareas, se muestran 4 puntos y un contador «+N», de modo que el total sigue visible y la celda no se rompe.
- **Lista del día**: ordenada por hora y, a igual hora, por orden de creación. Admite tareas simultáneas, nombres repetidos y tareas pasadas. Si el día no tiene tareas, muestra un estado vacío con un botón para crear una.
- **Crear y editar tareas**:
  - nombre obligatorio, que no tiene que ser único;
  - fecha (por defecto, el día seleccionado) y hora (por defecto, 10:00), con selector de hora y accesos rápidos;
  - color (azul por defecto) y descripción;
  - notificaciones a elegir de una lista (por defecto, «2 días antes»);
  - adjuntos con el selector de archivos del sistema y fotos con la cámara.
- **Repetición**: diaria, semanal, mensual, anual o personalizada («Repetir cada N horas, días, semanas, meses o años»), con fecha de fin elegida en un calendario. Mientras configuras la repetición, se muestran las 3 próximas repeticiones y cuántas tareas se crearán.
- **Instancias independientes**: cada repetición se guarda como una tarea propia. Editar o borrar una no afecta a las demás. Al borrar, también se puede eliminar la serie completa.
- **Detalle de tarea**: muestra todos los campos, los adjuntos con miniatura de las fotos, las notificaciones y la información de la serie.
- **Notificaciones reales**: usan AlarmManager y siguen funcionando tras reiniciar el dispositivo, cambiar la hora o la zona horaria y actualizar la app. Al pulsar una notificación se abre la tarea.
- **Tema oscuro**, interfaz pensada para el tacto y diseño adaptable: en pantallas de 600 dp o más (tablets o móvil en horizontal) el calendario y la lista se colocan uno al lado del otro.
- Interfaz en **español** y en **inglés**, según el idioma del dispositivo.

## Tecnologías

| Área | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3, Navigation Compose |
| Persistencia | Room 2.6.1 (SQLite), KSP |
| Asincronía | Coroutines y Flow |
| Imágenes | Coil 2.7 |
| Notificaciones | AlarmManager, BroadcastReceivers y NotificationCompat |
| Archivos | Storage Access Framework (`OpenMultipleDocuments`) y FileProvider |
| Cámara | `ActivityResultContracts.TakePicture` |
| Build | Gradle 8.11.1, Android Gradle Plugin 8.7.3 y version catalog |
| Tests | JUnit 4, kotlinx-coroutines-test, AndroidX Test y Compose UI Test |
| CI | GitHub Actions (build, tests unitarios, lint y tests en emulador API 34) |

minSdk 26 (Android 8.0), targetSdk/compileSdk 35 (Android 15).

## Arquitectura

La app tiene un solo módulo con capas separadas por paquetes:

```
com.apolilla.calendar
├── domain/            Lógica de negocio pura en Kotlin, sin dependencias de Android
│   ├── model/         Task, Attachment, Reminder, SeriesRef, TaskColor, TaskOrdering
│   ├── recurrence/    RecurrenceRule y RecurrenceEngine (generación de repeticiones)
│   ├── calendar/      MonthGrid (rejilla lunes-domingo) y DayIndicators (puntos)
│   ├── reminder/      ReminderOffset y ReminderPlanner (cálculo y despacho de avisos)
│   ├── repository/    Interfaces TaskRepository, ReminderScheduler y AttachmentCleaner
│   └── TaskService    Casos de uso: crear, editar, borrar, procesar recordatorios
├── data/              Implementación Room: entidades, DAO, mappers y RoomTaskRepository
├── notifications/     AlarmReminderScheduler, TaskNotifier, receivers y ReminderProcessor
├── files/             AttachmentManager (SAF, disponibilidad, abrir) y PhotoStore
├── camera/            rememberPhotoCapture: permiso, archivo destino y TakePicture
├── ui/                Pantallas Compose y ViewModels (home, edit, detail), tema y navegación
├── AppContainer       Inyección de dependencias manual
└── CalendarApp / MainActivity
```

- La UI solo conoce los ViewModels. Los ViewModels convierten el estado del formulario en un `TaskDraft` y delegan en `TaskService`.
- `TaskService` coordina la generación de repeticiones, el cálculo de recordatorios, la persistencia y los efectos secundarios (alarmas, notificaciones y limpieza de archivos). Lo hace a través de interfaces, lo que permite probarlo en la JVM con dobles en memoria.
- **Modelo de datos (Room)**:
  - `series`: la regla de repetición, la fecha de inicio, la fecha de fin y el número de instancias;
  - `tasks`: cada tarea o instancia, con su `seriesId`, `occurrenceIndex` y `originalDateTime`, que identifican la instancia dentro de la serie;
  - `attachments` y `reminders`: filas hijas con borrado en cascada.

  Las fechas se guardan como día de época y las horas como minuto del día, lo que permite ordenar directamente en SQL.

## Requisitos

- JDK 17.
- Android SDK con la plataforma 35 (Android Studio Ladybug o posterior la instala automáticamente).
- Para ejecutar: un dispositivo o emulador con Android 8.0 o superior.

## Clonar

```bash
git clone https://github.com/apolilla/calendar.git
cd calendar
```

## Compilar y ejecutar

Con Android Studio, abre la carpeta del proyecto, espera a que Gradle sincronice y pulsa **Run** sobre la configuración `app`.

Desde la línea de comandos (necesitas `ANDROID_HOME` o un `local.properties` con `sdk.dir`):

```bash
./gradlew assembleDebug          # compila el APK de depuración
./gradlew installDebug           # lo instala en el dispositivo o emulador conectado
./gradlew testDebugUnitTest      # tests unitarios (JVM)
./gradlew connectedDebugAndroidTest   # tests instrumentados (requiere dispositivo o emulador)
./gradlew lintDebug              # análisis estático
```

## Generar un instalable

- **APK de depuración**: `./gradlew assembleDebug` genera `app/build/outputs/apk/debug/app-debug.apk`, que se puede instalar directamente. También lo puedes descargar ya compilado desde la pestaña **Actions** de GitHub, en el artefacto `apks` de la última ejecución.
- **APK o AAB de release firmado**:
  1. Crea un keystore, por ejemplo con `keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias calendar`.
  2. Crea en la raíz del proyecto un archivo `keystore.properties`. Está en `.gitignore`, así que nunca se sube al repositorio.
     ```properties
     storeFile=release.jks
     storePassword=...
     keyAlias=calendar
     keyPassword=...
     ```
  3. Ejecuta `./gradlew assembleRelease` (APK) o `./gradlew bundleRelease` (AAB para Google Play). Sin `keystore.properties`, el release se genera sin firmar.

## Decisiones técnicas

- **Instancias materializadas**: al guardar una tarea repetida se crean todas sus instancias como filas independientes, en una sola transacción. La serie solo guarda la regla, como información y para poder borrar la serie completa. Editar una serie entera no está implementado, como permite el enunciado.
- **Cálculo de repeticiones**: cada repetición se calcula desde el inicio (`inicio + n × intervalo`), nunca desde la anterior. Así se evita la deriva al ajustar a fin de mes.
- **Meses sin ese día**: una tarea mensual el día 31 cae en el último día de los meses más cortos (31 ene → 28/29 feb → 31 mar → 30 abr…). Todos los meses tienen exactamente una repetición.
- **29 de febrero**: en las tareas anuales, los años no bisiestos usan el 28 de febrero.
- **Repetición por horas**: usa la hora de reloj local y puede pasar a los días siguientes. No se ajusta por el cambio de horario de verano.
- **Fecha de fin inclusiva**: se generan las repeticiones que caen en la fecha de fin y ninguna posterior. Si la fecha de fin es anterior al inicio, se muestra un error de validación.
- **Sin fecha de fin**: se generan repeticiones para los próximos 2 años (20 años en las anuales). En cualquier caso, el máximo es de **1000 instancias** por serie, para proteger el rendimiento y el almacenamiento. El formulario indica cuántas se crearán y avisa si se alcanza el límite.
- **Notificaciones con una sola alarma**: en lugar de una alarma por recordatorio, que podría superar el límite de unas 500 alarmas por app, los recordatorios se guardan en la base de datos con su hora de disparo. Siempre hay una única alarma programada para el más próximo. Al dispararse, se muestran los recordatorios vencidos, se marcan como disparados y se programa el siguiente. Gracias a esto:
  - cancelar o reprogramar al editar o borrar es simplemente recalcular desde la base de datos;
  - no hay duplicados: cada recordatorio se marca como disparado una sola vez, y el ID de la notificación es estable por tarea y antelación;
  - tras reiniciar el dispositivo, actualizar la app o cambiar la hora o la zona horaria, `SystemEventsReceiver` vuelve a programar la alarma. Al cambiar de zona horaria, además, recalcula las horas de disparo.
- **Recordatorios perdidos**: si el dispositivo estaba apagado, el aviso se muestra al arrancar, siempre que la tarea aún no haya empezado. Si vencieron varios de la misma tarea, solo se muestra el más reciente.
- **Recordatorios cuya hora ya pasó al guardar** (por ejemplo, «2 días antes» de una tarea de mañana): se omiten en lugar de dispararse tarde.
- **Alarmas exactas**: se usa `USE_EXACT_ALARM`, permitido a las apps de calendario en Android 13 y posteriores y concedido automáticamente, y `SCHEDULE_EXACT_ALARM` en Android 12. Si las alarmas exactas no están permitidas, se usa una alarma inexacta, que Android puede retrasar unos minutos.
- **Permiso de notificaciones** (Android 13 y posteriores): se pide al guardar una tarea que tiene notificaciones, el momento en que tiene sentido. Si se deniega, la tarea se guarda igualmente y la pantalla principal muestra un aviso con acceso a los ajustes.
- **Adjuntos**: se usa `ACTION_OPEN_DOCUMENT` con permisos persistentes (`takePersistableUriPermission`). Los archivos no se copian, para no duplicar almacenamiento. Si el archivo se mueve o se borra fuera de la app, aparece como «No disponible» y nunca provoca un cierre. El permiso se libera cuando ninguna tarea usa ya el archivo.
- **Fotos**: se guardan en el almacenamiento privado de la app (`files/photos`) y se exponen mediante FileProvider. Las instancias de una serie comparten la misma foto, que se borra cuando la deja de usar la última tarea. Si se cancela la captura, se elimina el archivo vacío.
- **Inyección de dependencias manual** (`AppContainer`): el grafo es pequeño y no compensa añadir Hilt.
- **Tema oscuro fijo**: es un requisito de la app, así que no depende del ajuste del sistema.

## Tests

- **Unitarios (JVM)**, en `app/src/test`:
  - `RecurrenceEngineTest`: repeticiones diarias, semanales, mensuales (incluido el día 31), anuales (incluido el 29 de febrero) y personalizadas por horas, días, semanas, meses y años; fecha de fin inclusiva, horizonte por defecto, límite de 1000 y vista previa.
  - `MonthGridTest`: semanas de lunes a domingo, meses de 4, 5 y 6 filas, navegación entre años, fechas pasadas e indicadores (0, 1 o muchas tareas y desbordamiento).
  - `ReminderPlannerTest`: cálculo de cada antelación, recordatorios omitidos, conservación del estado «disparado», reprogramación, deduplicación tras un apagado y alarma siguiente.
  - `TaskServiceTest`: creación, validación, nombres duplicados, tareas simultáneas, tareas pasadas, instancias independientes, edición, borrado, borrado de serie, limpieza de adjuntos, recordatorios que no se duplican y cambio de zona horaria.
  - `HomeViewModelTest`: lista y puntos reactivos, y navegación entre meses.
- **Instrumentados (emulador)**, en `app/src/androidTest`:
  - `RoomTaskRepositoryTest`: persistencia real en SQLite, orden, cascadas, series y datos que sobreviven a cerrar y reabrir la base de datos.
  - `ReminderNotificationTest`: publica una notificación real, comprueba que no se duplica y que se cancela al borrar la tarea.
  - `CreateTaskFlowTest`: flujo de interfaz para crear una tarea, verla en la lista y abrir su detalle; validación del nombre vacío y navegación entre meses.

Todos se ejecutan en GitHub Actions en cada push.

## Limitaciones conocidas

- No se puede editar una serie completa: cada cambio afecta solo a la instancia editada. Sí se puede borrar la serie completa.
- Las repeticiones se generan al crear la tarea, dentro del horizonte o fecha de fin y con un máximo de 1000. Una serie sin fecha de fin no se amplía automáticamente más allá de ese horizonte.
- La repetición por horas no ajusta los cambios de horario de verano: usa la hora local de reloj.
- Si Android mata el proceso mientras la cámara o el selector de archivos están abiertos, se pierden los campos del formulario que no se hayan guardado. La foto en curso sí se conserva.
- Un archivo adjunto de un proveedor que no ofrece permisos persistentes puede dejar de ser accesible tras reiniciar. La app lo muestra entonces como «No disponible».
- No hay sincronización en la nube ni con Google Calendar. Los datos son locales, aunque se incluyen en la copia de seguridad de Android.
- La cámara, el selector de archivos real y el reinicio físico del dispositivo no se cubren con tests automáticos, porque dependen de apps del sistema. Su lógica está aislada y la verificación es manual.
