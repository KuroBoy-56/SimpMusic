# Panel de cuentas (KuroMusic / YT Music Mod): cómo hablan las apps con el panel

Referencia para portar la integración del panel a las apps de escritorio (Windows y macOS), escrita leyendo el código real:

- **Android**: `androidApp/src/main/java/com/maxrave/simpmusic/` — `LoginActivity.kt`, `SimpMusicApplication.kt`, `MainActivity.kt`, `CrashManager.kt`, `service/rss/RssFeedNotifyWork.kt`, `service/backup/MediaAudioConfig.kt`.
- **iOS**: `composeApp/src/iosMain/kotlin/com/maxrave/simpmusic/ios/panel/` — `KuroPanel.kt`, `KuroGate.kt`, `KuroDeviceId.kt`, `KuroCrashReporter.kt`; `core/data/src/iosMain/.../mediaservice/ios/IosPlaybackDiagnostics.kt` y su conexión en `MainViewController.kt`.
- **Servidor (PHP)**: repo `panelweb`, rama `panelyt` — todo `api/`, `.htaccess` raíz y `api/.htaccess`, `includes/functions.php` / `includes/security.php`, y las páginas que configuran la API (`manager.php`, `manager_up_now.php`, `desk_up_manager.php`, `manager_days.php`, `messages.php`, `downloads.php`, `adduser.php`, `manager_errores.php`).

Fecha del análisis: 2026-10-16. Esto describe el código de los repos, no el servidor en vivo: donde el código no basta para afirmar algo, el texto lo marca como **AMBIGUO** o **VERIFICAR**.

> **Versión pública.** Las URLs reales, la clave del token, el secreto de Cloudflare y los valores ofuscados no aparecen aquí. `<PANEL_BASE>` es la URL base real del panel: es la que va cifrada en `KuroPanel.kt` (`LOGIN_URL_BYTES`) y en `LoginActivity.kt` (el `intArrayOf(...)` de `performLogin`). `<SECRET_KEY>` es la misma constante que `SECRET_KEY` en `KuroPanel.kt` y `secretKey` en `LoginActivity.kt`. Los valores en claro están en la documentación del repo privado `panelweb`.

---

## 0. Resumen

| # | Endpoint | Lo llama Android | Lo llama iOS | Cuándo | Método |
|---|---|---|---|---|---|
| 1 | `api/player_api.php` | `LoginActivity.performLogin` y la revalidación de `MainActivity` | `KuroPanel.login` y `KuroPanel.revalidate` | Al pulsar ENTRAR; al abrir la app | GET (query) |
| 2 | `api/chack_api.php` (**así, con la errata**) | `SimpMusicApplication.validarCuentaSilenciosamente` | No (la constante existe pero no se usa) | En cada arranque del proceso | GET (query) |
| 3 | `api/check_days.php` | `MainActivity.verificarDiasRestantes` | `KuroPanel.daysLeftAlert` | Al abrir la app | GET (query) |
| 4 | `api/check_update.php` | `MainActivity.verificarActualizacionApp` y `RssFeedNotifyWork` (cada 24 h) | `KuroPanel.updateInfo` | Al abrir la app | GET (query) |
| 5 | `api/api_messages.php` | `MainActivity.revisarComunicadoBackground` (el resultado no se usa) y `RssFeedNotifyWork` (cada 24 h) | No | Al abrir la app; cada 24 h | GET (query) |
| 6 | `api/log_error.php` | `CrashManager` (crash no capturado) | `KuroCrashReporter` (crash, login, reproducción, descarga, arranque fallido) | Cuando ocurre el error | POST JSON |
| 7 | `api/desktop_update.php` | No | No | Pensado para el escritorio | GET (query) |

Ninguna app tiene un temporizador de revalidación: todo se pide **al arrancar**. Ninguna app manda telemetría por canción (`algoritmo.php` existe en el panel, pero nadie lo llama).

---

## 1. Infraestructura común

### 1.1 URL base y extensión

- Base: `<PANEL_BASE>`. Todos los endpoints son `<PANEL_BASE>/api/<archivo>.php`.
- Siempre HTTPS. Las apps usan la extensión `.php` explícita. `api/.htaccess` también acepta la URL sin extensión (`RewriteCond %{REQUEST_FILENAME}\.php -f` → `$1.php`), pero ninguna app depende de eso.
- Las URLs de descarga de actualizaciones las genera el panel con otro host fijo: `<DOWNLOAD_BASE>/api/update/…` (APK de Android), `<DOWNLOAD_BASE>/api/updown/…` y `<DOWNLOAD_BASE>/api/updown_pc/…` (escritorio).

### 1.2 Cloudflare y `X-Origin-Secret`

- Todo el panel va detrás de Cloudflare. Según `INSTRUCCIONES.md`, un worker de Cloudflare (`worker.js`, que no está en el repo) añade la cabecera `X-Origin-Secret` con el valor de su variable `ORIGIN_SECRET`.
- `.htaccess` raíz: si una petición bajo `/youtube/panel` llega sin esa cabecera exacta, responde **403** (`RewriteRule ^ - [F,L]`).
- El valor esperado es `<X_ORIGIN_SECRET>`, en el `.htaccess` raíz del repo privado.
- **Las apps NO mandan esta cabecera.** La recibe el origen porque la pone Cloudflare. La app de escritorio tampoco debe llevarla incrustada.
- **AMBIGUO:** `api/.htaccess` declara su propio `RewriteEngine on`. En Apache, las reglas de reescritura de un `.htaccess` de subdirectorio **sustituyen** a las del padre (salvo `RewriteOptions Inherit`), así que puede que la comprobación del secreto no se aplique a `/api/` en el origen. A un cliente que entra por Cloudflare le da igual.
- `login_bridge.php` (servidor → servidor) sí manda la cabecera él mismo; ver §5.8.

### 1.3 Otras capas del servidor que afectan a la API

- **Licencia del panel**: `player_api.php` y `check_api.php` incluyen `includes/functions.php`, que ejecuta `includes/security.php` → `verify_panel_integrity()` en cada petición. Si el host (`X-Panel-Host` › `X-Forwarded-Host` › `Host`) no es un dominio maestro (los de `includes/master_domains.json`) y no hay licencia válida, devuelve una **página HTML con HTTP 200** en lugar del JSON. El escritorio debe usar exactamente la misma base que las apps móviles.
- **Sesión PHP**: `time_config.php` y `functions.php` llaman a `session_start()`, así que cada llamada a `player_api.php` / `check_api.php` responde con `Set-Cookie: PHPSESSID=…`. Las apps no guardan cookies (ni HttpURLConnection sin CookieManager ni el cliente Ktor del panel). Conviene un cliente HTTP propio para el panel, sin almacén de cookies.
- **Páginas de error**: `ErrorDocument 500|429|502|503|504|508` → `/youtube/panel/error.html` (HTML, con el código original). Según `INSTRUCCIONES.md`, el worker reintenta una vez en 429/5xx y "la API de las apps recibe la respuesta original".
- **CORS**: `Access-Control-Allow-Origin: *` y compañía. A las apps nativas no les afecta.
- **Archivos bloqueados por web**: `.db.db`, `*.db|sqlite|sq3|log|txt|ini|md|bak|lock`, `-journal|-wal|-shm`, y `usuarios_log.json`, `blocked_ips.json`, `master_domains.json`, `config_days.json`, `config_downloads.json`, `.license.json`. Siguen siendo públicos `versions.json`, `yt_versions.json` y `yt_versions_pc.json`.
- **Zona horaria**: todo es `America/Panama` (UTC−5 fijo, sin horario de verano). `time_config.php` pone `Etc/GMT+5` (también UTC−5) por defecto, y `player_api.php`, `check_api.php`, `check_days.php`, `log_error.php` y `login_bridge.php` fuerzan `America/Panama`.

### 1.4 Cabeceras que mandan las apps

Ninguna personalizada. Android usa `HttpURLConnection` con el User-Agent por defecto (`Dalvik/2.1.0 (Linux; U; Android …)`). iOS usa Ktor con el motor Darwin (`NSURLSession`), con el User-Agent por defecto del sistema y sin plugin `UserAgent`. El crash reporter solo añade `Content-Type: application/json`.

---

## 2. URLs ofuscadas y cómo descifrarlas

Las apps no guardan las URLs en claro. Hay dos esquemas.

**A. Desplazamiento −5** (`LoginActivity`, `MainActivity`, `RssFeedNotifyWork`, `KuroPanel.shiftDecode`): un `IntArray` en el que cada valor es el código del carácter más 5.

```kotlin
// KuroPanel.kt
private fun shiftDecode(bytes: IntArray): String = buildString { bytes.forEach { append((it - 5).toChar()) } }
```

**B. Hex → Base64 → XOR** (`SimpMusicApplication.decryptString`, `MainActivity.desencriptarUrl`, `KuroPanel.decryptString`): la cadena hex se lee como bytes, esos bytes son texto Base64, y lo que sale del Base64 se pasa por un XOR cíclico con la clave ASCII `"<XOR_KEY>"`.

```kotlin
// KuroPanel.kt
val base64 = encryptedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().decodeToString()
val xored = Base64.decode(base64)
val key = "<XOR_KEY>".encodeToByteArray()
ByteArray(xored.size) { i -> (xored[i].toInt() xor key[i % key.size].toInt()).toByte() }.decodeToString()
```

Los dos esquemas se reproducen en unas líneas de cualquier lenguaje siguiendo exactamente los pasos de arriba (así se descifró la tabla siguiente).

| Constante (dónde) | Esquema | Endpoint al que apunta |
|---|---|---|
| `LOGIN_URL_BYTES` (KuroPanel); `encryptedBytes` de `LoginActivity.performLogin` y de la revalidación de `MainActivity` | A | `<PANEL_BASE>/api/player_api.php` |
| `DAYS_URL_BYTES` (KuroPanel); `verificarDiasRestantes` (MainActivity) | A | `<PANEL_BASE>/api/check_days.php` |
| `UPDATE_URL_BYTES` (KuroPanel); `verificarActualizacionApp` (MainActivity); `encUpdate` (RssFeedNotifyWork) | A | `<PANEL_BASE>/api/check_update.php` |
| `encMsg` (RssFeedNotifyWork) | A | `<PANEL_BASE>/api/api_messages.php` |
| `encryptedUrl` de `revisarComunicado*` (MainActivity) | B | `<PANEL_BASE>/api/api_messages.php` |
| `ENCRYPTED_API_PATH` (SimpMusicApplication; KuroPanel, sin uso) | B | `<PANEL_BASE>/api/chack_api.php` ← **errata** |
| `ENCRYPTED_ERROR_API_PATH` (SimpMusicApplication; KuroPanel) | B | `<PANEL_BASE>/api/log_error.php` |
| `VALID_SIGNATURE_HASH` (SimpMusicApplication) | B | Base64 del SHA-256 del certificado de firma (no es una URL) |

**Errata confirmada:** `ENCRYPTED_API_PATH` se descifra como `chack_api.php`. El archivo del panel se llama `check_api.php`. Volviendo a cifrar `check_api.php` sale un bloque distinto (`…LCMwMSQU…` en lugar del `…LCM0MSQU…` de la constante), así que no es un fallo de descifrado. Consecuencias en §5.2.

---

## 3. El identificador de dispositivo (`mac`)

No es una MAC real: son **16 caracteres hexadecimales en mayúsculas, en 8 pares separados por `:`**, por ejemplo `A1:B2:C3:D4:E5:F6:78:9A`.

### 3.1 Android: a partir de `ANDROID_ID`

Hay tres copias idénticas (`LoginActivity`, `SimpMusicApplication`, `MainActivity`):

```kotlin
private fun getCustomMacAddress(): String {
    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "1A2B3C4D5E6F7A8B"
    var processed = androidId.trimStart('0')          // 1. quitar ceros a la izquierda
    if (processed.isEmpty()) processed = "1A2B3C4D5E6F7A8B"  // 2. respaldo si no queda nada
    processed = processed.padEnd(16, 'A')              // 3. rellenar con 'A' por la DERECHA hasta 16
    processed = processed.substring(0, 16).uppercase() // 4. cortar a 16 y pasar a mayúsculas
    return processed.chunked(2).joinToString(":")      // 5. pares separados por ':'
}
```

`ANDROID_ID` son 16 hex en minúsculas, distintos por app firmante y usuario desde Android 8. Cambia con un restablecimiento de fábrica, pero no al reinstalar la app.

### 3.2 iOS: a partir de `identifierForVendor`, guardado en el Keychain

`KuroDeviceId.kt`:

1. Si hay valor guardado y válido (16 caracteres `[0-9A-F]`), se usa ese. Orden de lectura: Keychain (`kSecClassGenericPassword`, servicio `com.kuroboy56.kuromusic.device`, cuenta `device_id`) → `NSUserDefaults["kuro.device_id"]`.
2. Si no hay ninguno, se deriva: `UIDevice.identifierForVendor.UUIDString` sin guiones (32 hex en mayúsculas; si es nulo, `"1A2B3C4D5E6F7A8B"`) → `trimStart('0')` → si queda vacío, el respaldo → `padEnd(16,'A')` → `substring(0,16)` → `uppercase()`.
3. El valor se escribe en el Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) y en `NSUserDefaults`, y queda en caché en memoria. Va al Keychain porque `identifierForVendor` cambia cuando se borran todas las apps del mismo proveedor, y el Keychain sobrevive a la reinstalación.
4. `mac()` = los 16 caracteres en pares con `:`.

### 3.3 Re-normalización del servidor (`player_api.php`)

```php
$raw_mac_clean = str_replace(':', '', $raw_mac);
$processed = strtoupper(substr(str_pad(ltrim($raw_mac_clean, '0'), 16, 'A'), 0, 16));
$incoming_mac = implode(':', str_split($processed, 2));
```

Pasos: quitar los `:`, `ltrim` de ceros, rellenar con `A` a la derecha hasta 16, cortar a 16, mayúsculas y pares con `:`. Sobre lo que mandan las apps es **idempotente**. Diferencias que conviene conocer:

- Con una entrada vacía, el servidor produce `AA:AA:AA:AA:AA:AA:AA:AA`. La app nunca manda vacío: usa el respaldo `1A:2B:3C:4D:5E:6F:7A:8B`.
- El servidor no comprueba que sean hex: cualquier carácter pasa.
- **El token (§4) se calcula con el `mac` CRUDO, tal como llega en el parámetro, antes de normalizar.** La base de datos guarda y compara la versión normalizada.

| Entrada (origen) | Lo que manda la app | Lo que guarda el servidor |
|---|---|---|
| `0a1b2c3d4e5f6789` (ANDROID_ID) | `A1:B2:C3:D4:E5:F6:78:9A` | igual |
| `9f3c` (ID corto) | `9F:3C:AA:AA:AA:AA:AA:AA` | igual |
| `00E621E1F8C36C495A93FC0C247A3E6E` (IFV sin guiones) | `E6:21:E1:F8:C3:6C:49:5A` | igual |
| vacío / nulo | `1A:2B:3C:4D:5E:6F:7A:8B` | igual |
| `00:11:22:33:44:55` (MAC real, si alguien la mandara) | — | `11:22:33:44:55:AA:AA:AA` |

(Comprobado contra PHP 8.3.)

### 3.4 Dónde viaja el `mac`

- `player_api.php`: en el token, y para el registro y la comprobación del dispositivo.
- `check_days.php`: Android e iOS lo mandan, pero el servidor **lo ignora**.
- `log_error.php`: solo iOS lo manda, en el campo `mac` (se guarda en la columna `mac`). iOS también lo incluye en `dispositivo` (`… ID <mac>`).

---

## 4. El token de seguridad

```
token = hex_minúsculas( SHA-256( UTF-8( username + mac + hora + SECRET_KEY ) ) )
hora  = fecha actual en America/Panama con formato "yyyy-MM-dd-HH"   (24 h, con ceros, dígitos ASCII)
```

- `username` es exactamente la cadena del parámetro `username`: después de `trim()` y antes de codificarla para la URL. Distingue mayúsculas, aunque la búsqueda del usuario en la base de datos no las distinga.
- `mac` es exactamente la cadena del parámetro `mac`, con sus `:`.
- `SECRET_KEY` = `"<SECRET_KEY>"`, el mismo valor en las tres copias de Android, en `KuroPanel.SECRET_KEY`, en `player_api.php`, en `check_api.php` y en `security_config.php` (`PLAYER_API_SECRET`).
- La contraseña **no** entra en el token.

Android (`LoginActivity` / `SimpMusicApplication` / `MainActivity`):

```kotlin
private fun generateSecurityToken(user: String, mac: String): String {
    val secretKey = "<SECRET_KEY>"
    val format = SimpleDateFormat("yyyy-MM-dd-HH", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/Panama") }
    val currentHourDate = format.format(Date())
    val stringToHash = "$user$mac$currentHourDate$secretKey"
    val bytes = MessageDigest.getInstance("SHA-256").digest(stringToHash.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
```

(`MainActivity` usa `"%02x".format(it)` sin `and 0xFF`. Da el mismo resultado: el `Formatter` de Java suma 2⁸ a los `Byte` negativos.)

iOS (`KuroPanel.securityToken`):

```kotlin
val t = Clock.System.now().toLocalDateTime(TimeZone.of("America/Panama"))
val hour = "${t.year}-${(t.month.ordinal + 1).pad()}-${t.day.pad()}-${t.hour.pad()}"   // pad = 2 dígitos
return sha256Hex("$user$mac$hour$SECRET_KEY")                                         // CC_SHA256, hex minúsculas
```

Servidor (`player_api.php`; `check_api.php` hace lo mismo):

```php
date_default_timezone_set('America/Panama');
if ($app_token !== '') {
    $server_token = hash('sha256', $username . $raw_mac . date("Y-m-d-H") . $secret_key);
    if ($app_token !== $server_token) deny("Conexión rechazada: Protocolo de seguridad fallido.");
}
```

Para el escritorio (JVM), con la API estándar:

```kotlin
val hour = ZonedDateTime.now(ZoneId.of("America/Panama"))
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH", Locale.ROOT))
val token = MessageDigest.getInstance("SHA-256")
    .digest("$user$mac$hour$SECRET_KEY".toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
```

Reglas del servidor:

- **Token vacío → no se comprueba** y la petición sigue como si no hubiera token. Es la "Fase 1": el código comentado para exigirlo ("Fase 2") está en `player_api.php` y en `check_api.php`, pero no activo. El escritorio debe mandarlo siempre, por si se activa la Fase 2.
- Token presente pero distinto → denegación (HTTP 200, `auth: 0`, mensaje `Conexión rechazada: Protocolo de seguridad fallido.`).
- Solo vale la **hora en curso**: no hay ventana de tolerancia. La comparación es `!==` (no en tiempo constante).

Vectores de prueba:

- Hora: epoch `1792141200` (2026-10-16 09:00:00 UTC) → `2026-10-16-04`.
- Token: compara el de tu app con `php -r 'echo hash("sha256", "demo"."A1:B2:C3:D4:E5:F6:78:9A"."2026-10-16-07"."<SECRET_KEY>");'`. Deben coincidir.

---

## 5. Endpoints en detalle

### 5.1 `player_api.php`: login y revalidación

**Petición, tal como la mandan las apps:**

```
GET <PANEL_BASE>/api/player_api.php?username=<u>&password=<p>&mac=<mac>&token=<token>
```

- Codificación: Android usa `URLEncoder.encode(x, "UTF-8")` (el espacio pasa a `+` y `:` a `%3A`). iOS usa `parameter()` de Ktor. PHP lo decodifica igual en ambos casos.
- El servidor lee `$_REQUEST`, así que **POST `application/x-www-form-urlencoded` también vale** (`login_bridge.php` lo usa así). Alias que acepta: `username|user|uname`, `password|pass|pword`, `code`, `mac`, `token`. A todos les aplica `trim()`.

| Llamante | Timeout | Reintentos |
|---|---|---|
| Android `LoginActivity.performLogin` | conexión 8 s, lectura 8 s | ninguno |
| Android `MainActivity` (revalidación) | 5 s / 5 s | 3 intentos con 2 s entre ellos. Un 401/403 corta y devuelve `AUTH_ERROR`. Tras el tercer fallo devuelve `null`. |
| iOS `KuroPanel.login` | conexión 8 s, petición 10 s, socket 8 s | ninguno |
| iOS `KuroPanel.revalidate` | igual | 3 intentos con 2 s entre ellos. Un 401/403 da `AuthError`; sin respuesta, `Unreachable`. |

**Lógica del servidor, paso a paso:**

1. Carga `time_config.php` (sesión y zona horaria) e `includes/functions.php` (sesión, BD `api/.db.db` en modo WAL, licencia). Pone `Content-Type: application/json`.
2. Lee y recorta `username`, `password`, `code`, `mac` y `token`. Fija `America/Panama`.
3. **Token** (§4): si llega y no coincide, deniega.
4. Normaliza el `mac` a `$incoming_mac` (§3.3).
5. Elige el modo:
   - **a) Solo MAC** (`username`, `password` y `code` vacíos): `SELECT * FROM menualuser WHERE mac_address LIKE '%<mac>%'` y luego coincidencia exacta dentro de la lista separada por comas. Si la encuentra, toma usuario y contraseña de la BD.
   - **b) Usuario + contraseña** (los dos no vacíos): `WHERE LOWER(username) = LOWER(:u)` (no distingue mayúsculas). La contraseña vale si `$password === trim(db_pass)`, o `urldecode($password) === db_pass`, o `html_entity_decode($password) === db_pass` (aquí sí se distinguen mayúsculas).
   - **c) Código** (`code` no vacío y no se cumple b): `SELECT * FROM codes WHERE code = :code`; usuario y contraseña pasan a ser el propio código. *La tabla `codes` no existe en la BD del repo.*
   - Si llega usuario sin contraseña (o al revés) y no hay `code`, no entra en ningún modo y responde `Invalid login`.
6. Usuario encontrado (modos a y b):
   - `expired = time() > strtotime(expire_date)`. `expire_date` es `Y-m-d H:i:s` en hora de Panamá.
   - Sin caducar:
     - Si el MAC ya está en `mac_address` (lista separada por comas), es válido.
     - Si no está y `quota > 0`: añade el MAC a la lista y hace `quota - 1`. Son dos `UPDATE` sin transacción. El dispositivo queda **registrado**.
     - Si no está y `quota == 0`: `Superó el límite de dispositivos disponibles`.
   - Si es válido: `auth=1`, `exp = strtotime(expire_date)`, `created = strtotime(created_at)` o la hora actual.
   - Si está caducado: `Expired`.
7. Código (modo c): caducado si `expires_at` está vacío o ya pasó. Si no, `auth=1` y `exp = strtotime(expires_at) + 86399`. **No comprueba ni registra el MAC.**
8. **Bloqueo**: `player_api.php` no mira la columna `blok`. Pero bloquear desde el panel (`adduser.php`) pone `blok=1` **y retrasa `expire_date` 20 años**, así que la cuenta bloqueada responde `Expired`. Al desbloquear se le suman los 20 años, solo si la fecha es anterior a 2015-01-01.
9. Responde `success()` o `deny()`, siempre con **HTTP 200** (salvo que PHP falle con un error fatal).

**Respuesta de éxito** (formato tipo Xtream-Codes):

```json
{
  "user_info": {
    "username": "<el enviado>",
    "password": "<el enviado, en claro>",
    "message": "Logged in successfully",
    "auth": 1,
    "status": "Active",
    "exp_date": "<unix, string>",
    "is_trial": "0",
    "active_cons": "0",
    "created_at": "<unix, string>",
    "max_connections": "1",
    "allowed_output_formats": ["m3u8", "ts"]
  },
  "server_info": {
    "url": "<HTTP_HOST o <HOST_FALLBACK>>", "port": "25443", "https_port": "25463",
    "server_protocol": "http", "rtmp_port": "25444", "timezone": "America/Panama",
    "timestamp_now": 1792141200, "time_now": "2026-10-16 04:00:00"
  }
}
```

`success()` calcula `days_left`, pero **no lo incluye** en la respuesta.

**Respuesta de denegación**: la misma forma, con `auth: 0`, `status: "Disabled"` (siempre), `max_connections: "0"` y el motivo en `message`.

| `user_info.message` | Cuándo | `exp_date` | ¿Devuelve usuario y contraseña? |
|---|---|---|---|
| `Conexión rechazada: Protocolo de seguridad fallido.` | El token no coincide | `"0"` | no (vacíos) |
| `Invalid login` | No existe el usuario, la contraseña es incorrecta, faltan datos o el MAC no está registrado (modo a) | `"0"` | sí, los enviados |
| `Expired` | Caducado o bloqueado; código caducado | `"0"` (usuario) / el del código | sí |
| `Superó el límite de dispositivos disponibles` | MAC nuevo con `quota = 0` | `"0"` | sí |
| `Login failed or Expired` | Solo si el mensaje viniera vacío (en la práctica, nunca) | — | — |

**Cómo lo interpreta cada app:**

| Caso | Android `LoginActivity` | iOS `KuroPanel.login` |
|---|---|---|
| HTTP ≠ 200 | `Error del servidor: <código>` | igual |
| Excepción (red, DNS, TLS, timeout, **JSON que no se puede leer**) | `Error de red: revisa tu conexión` | Red: `Error de red: revisa tu conexión (<Excepción>)` y reporte `LOGIN_RED`. JSON ilegible: `Respuesta de servidor inválida` y reporte `LOGIN_RESPUESTA` (los primeros 2000 caracteres del cuerpo van en `stacktrace`) |
| Sin `user_info` | `Respuesta de servidor inválida` | igual |
| `auth == 1` con `status` Expired/Banned/Disabled | `Cuenta inactiva: <status>` (no ocurre: el éxito siempre es `Active`) | igual |
| `auth == 1` | Guarda `isLoggedIn=true`, `saved_user`, `saved_pass`. **No** fija `sync_telemetry_timestamp`. Anima el logo 1,8 s y abre `MainActivity` | Guarda `kuro.isLoggedIn=true`, `kuro.saved_user`, `kuro.saved_pass` y `kuro.sync_telemetry_timestamp=ahora`. Anima 1,8 s y luego `completeLogin()` abre la puerta |
| `auth != 1` | Muestra `user_info.status`, que **siempre es "Disabled"** | `denyMessage()`: `""`, `Invalid login` o `Login failed or Expired` → `Usuario o contraseña incorrectos`; `Expired` → `Cuenta vencida: contacta a tu proveedor para renovar`; cualquier otro → el mensaje tal cual |

Antes de llamar, las dos apps recortan usuario y contraseña. Si alguno queda vacío, muestran `Por favor, complete los campos` sin hacer la petición.

**Revalidación:**

| Resultado | Android `MainActivity` (en `setContent` → `LaunchedEffect`) | iOS `KuroPanel.revalidate` |
|---|---|---|
| Requisito para llamar | `saved_user` y `saved_pass` no vacíos | `isLoggedIn`, más usuario y contraseña no vacíos |
| 401 / 403 | `prefs.edit().clear()` (**borra todo AppPrefs**, credenciales incluidas) y va a `LoginActivity` | `logout()` |
| Sin respuesta tras 3 intentos | No hace nada (aquí no hay control offline) | `checkOfflineTime()`: tolerancia de 7 días |
| 200 con JSON ilegible | **`JSONObject(response)` lanza dentro del `LaunchedEffect` y no se captura: crash** | `checkOfflineTime()` |
| 200: `auth` (de `user_info.auth`; iOS también mira `auth` en la raíz) == 1 y `status` ∉ {Expired, Banned, Disabled} | Nada | `sync_telemetry_timestamp = ahora` |
| Cualquier otro 200 | `clear()` y `LoginActivity` | `logout()` |

### 5.2 `check_api.php`, que Android llama como `chack_api.php`

**Lo que hace Android** (`SimpMusicApplication.validarCuentaSilenciosamente`, en cada `Application.onCreate`, también cuando el proceso arranca solo por el servicio de música o por WorkManager): solo si `isLoggedIn` y hay usuario y contraseña guardados, llama en segundo plano a `GET <ENCRYPTED_API_PATH>?username=…&password=…&mac=…&token=…`, con 5 s / 5 s y sin reintentos.

- HTTP 200: `auth = user_info?.auth ?: root.auth`. Si vale 1, `sync_telemetry_timestamp = ahora`. Si no, `expulsarUsuario()`.
- HTTP ≠ 200 o excepción: `comprobarTiempoOffline(7 días)`:
  - si `sync_telemetry_timestamp == 0`, lo pone a la hora actual;
  - si no, y han pasado más de 7 días (`604 800 000 ms`), `expulsarUsuario()`.
- `expulsarUsuario()`: `isLoggedIn=false` y `sync_telemetry_timestamp=0` (**conserva** `saved_user` y `saved_pass`), abre `LoginActivity` con `NEW_TASK|CLEAR_TASK` y luego `killProcess`.

**Lo que haría `check_api.php`** (el archivo que sí existe):

- Si falta el usuario o la contraseña: `{"status":"error","auth":0}`.
- Token mal: `{"auth":0,"status":"Conexión rechazada: Protocolo de seguridad fallido."}`.
- Si no: busca el usuario sin distinguir mayúsculas y compara la contraseña (`===` o `urldecode`); mira la caducidad y una columna `status`, que **no existe** en `menualuser` (así que siempre cuenta como `Active`).
- Responde `{"auth":1,"status":"Active"}` o `{"auth":0,"status":"Disabled"|"Expired"}`, sin `user_info`. No registra ni comprueba el MAC ni la cuota.

**VERIFICAR en producción (riesgo alto):** si `chack_api.php` no existe en el servidor (en el repo no está), Android recibe siempre un 404 y entra siempre por la vía offline. Como el login no fija la marca de tiempo y la revalidación de `MainActivity` no la actualiza, la marca se fija en el primer arranque tras iniciar sesión y nunca se renueva. **Resultado: Android saca al usuario 7 días después del primer arranque y le obliga a volver a escribir sus datos**, y así cada semana. Se comprueba con `curl -i <base>/api/chack_api.php`. Arreglarlo (en la app, o con una copia o alias `chack_api.php` en el servidor) es asunto de Android: el escritorio no debe portar este camino.

### 5.3 `check_days.php`: aviso de días restantes

**Petición**: `GET …/api/check_days.php?username=<u>&mac=<mac>&token=<token>`. El servidor solo lee `$_GET['username']`, así que tiene que ir en la query. `mac` y `token` **se ignoran**.

- Android (`MainActivity.verificarDiasRestantes`): con `fetchPanelDataWithRetry` (5 s / 5 s, 3 intentos), si hay `saved_user`.
- iOS (`KuroPanel.daysLeftAlert`): con `fetchWithRetry`, si hay `kuro.saved_user`.

**Lógica del servidor:**

1. `ob_clean()`, `America/Panama`, abre `api/.db.db` por PDO.
2. Sin `username`: `{"status":"error","message":"Falta usuario"}`. Usuario no encontrado (sin distinguir mayúsculas): `{"status":"error","message":"Usuario no encontrado"}`. Sin BD: `…"Base de datos no encontrada"`. Excepción: `…"Error DB"`.
3. Con `blok == 1`: `{"status":"success","days_left":-1,"alert_title":"¡CUENTA BLOQUEADA!","alert_msg":"Tu cuenta ha sido suspendida."}`. Estos textos son fijos y no se editan en el panel.
4. Si no: `days = (expire_date a las 00:00) − (hoy a las 00:00)` en días con signo (`%R%a`).
5. Para `days` 3, 2, 1 o 0, `alert_title` y `alert_msg` salen de `config_days.json` (claves `title_N` / `msg_N`, editadas en `manager_days.php`; si faltan, hay textos por defecto). Para cualquier otro valor, título y mensaje van **vacíos**.
6. `{"status":"success","days_left":<int>,"alert_title":"…","alert_msg":"…"}`.

**Cómo lo interpreta cada app:**

- **Android**: si `status == "success"` y `days_left` está entre 0 y 3, muestra un diálogo **no cancelable** con el título en mayúsculas (`¡AVISO!` si viene vacío), el mensaje (`Tu suscripción está por vencer.` si viene vacío) y el botón `ENTENDIDO`. **El caso −1 (cuenta bloqueada) no se muestra en Android.**
- **iOS**: si `status == "success"`, el título no está vacío y `days_left` está entre −1 y 3, muestra un `AlertDialog` que sí se puede cerrar. Por tanto **sí** muestra el bloqueo.
- Ninguna de las dos cierra la sesión por esto. El cierre llega por `player_api`, que ve la cuenta bloqueada como `Expired`.

### 5.4 `check_update.php`: actualizaciones del móvil

**Petición**: Android `GET …/api/check_update.php?version=<versionCode>`. iOS `…?version=<versionCode>&platform=ios`. **El servidor ignora todos los parámetros** y devuelve siempre la última versión.

**Lógica del servidor**: lee la tabla `app_versions` (la escribe `manager.php` al subir un `.apk`, ver §8). Toma `ORDER BY version_code DESC LIMIT 1`, convierte `version_code` a int y `is_mandatory` / `is_obsolete` a bool, y añade `obsolete_versions` = la lista de `version_code` con `is_obsolete = 1`.

```json
{"version_code":27,"version_name":"2.5.0","is_mandatory":false,"is_obsolete":false,
 "release_notes":"…","download_url":"<DOWNLOAD_BASE>\/api\/update\/<ts>_<nombre>.apk",
 "date_added":"Y-m-d H:i:s","obsolete_versions":[25,26]}
```

(Según la instalación también puede venir `"id"`.) Errores: **404** `{"error":"Database not found"}` o `{"error":"No versions found in database"}`; **500** `{"error":"Database connection failed"}`.

**Cómo lo interpreta cada app:**

- **Android (`verificarActualizacionApp`)**: si `version_code > versionCode local`, espera 600 ms y muestra el diálogo. Es obligatoria si `is_mandatory` vale `"1"`, `"true"` o `true`, **o** si el `versionCode` local está en `obsolete_versions`. Un `obsolete_versions` solo cuenta cuando además hay una versión más nueva. Si es obligatoria: diálogo no cancelable, sin `MÁS TARDE` y en rojo; si no, en azul. `ACTUALIZAR` descarga el APK **dentro de la app** (`cacheDir/updates/update-<versionName>.apk`, conexión 15 s y lectura 30 s, sigue redirecciones, barra de progreso) y abre el instalador del sistema. `MÁS TARDE` encola un `RssFeedNotifyWork` de una sola vez.
- **`RssFeedNotifyWork`**: se repite cada 24 h con red mientras el ajuste `blog_notification_enabled` (por defecto `TRUE`) esté activo. Llama a `check_update.php?version=<code>` y a `api_messages.php` sin parámetros, con 8 s / 8 s y sin reintentos propios (`Result.retry()` si hay excepción), y guarda cada resultado en las notificaciones internas de la app (`NotificationEntity`, tipo blog), deduplicando por `link`: `sys_upd_<version_code>` para la actualización y `msg_<id>` para cada comunicado.
- **iOS (`updateInfo`)**: solo ofrece la actualización si `version_code > local` **y** hay un enlace de iOS: `ios_download_url`, o un `download_url` que no termine en `.apk`. Abre el enlace en Safari; si es obligatoria, el diálogo no se cierra. **En la práctica iOS nunca muestra actualizaciones**: la tabla no tiene columna `ios_download_url` y `manager.php` solo acepta `.apk`.

### 5.5 `desktop_update.php` y `yt_versions_pc.json`: actualizaciones del escritorio

**Petición**: `GET …/api/desktop_update.php?version_pc=<versión en texto, p. ej. 2.5.0>`. Si falta, se toma `0.0.0`.

**Lógica del servidor** (tabla `pc_versions`, escrita por `desk_up_manager.php`):

1. `ob_clean()`. Si no hay BD o tabla, o si algo lanza una excepción: `{"update_available":false}`.
2. `latest` = la fila con **mayor `id`**, es decir, la última insertada, no la versión más alta.
3. `obsolete_versions` = la lista de `version_pc` (texto) con `is_obsolete = 1`.
4. Si `version_compare(version_pc_cliente, latest.version_pc, '<')`:

```json
{"update_available":true,"is_mandatory":false,"obsolete_versions":["1.4.0"],
 "changelog":"<release_notes>","download_url":"<DOWNLOAD_BASE>/api/updown_pc/<archivo codificado>"}
```

   Si no: `{"update_available":false,"obsolete_versions":[…]}`.

Detalles que el cliente tiene que saber:

- **La respuesta no trae el nombre de la versión nueva.** El texto de novedades viene en `changelog`, no en `release_notes`.
- **Hay un solo `download_url` para todos los sistemas de escritorio**: `pc_versions` no tiene columna de plataforma. `desk_up_manager.php` no sube archivos: deja **elegir** uno que ya esté en `api/updown_pc/` (subido por FTP), y solo lista `.exe`, `.msi` y `.zip`. **Un `.dmg` o `.pkg` no se puede seleccionar.**
- La versión comparada es texto libre que escribe el administrador, con la semántica de `version_compare` de PHP: `1.10.0 > 1.9.0`, y un sufijo `-dev`, `alpha`, `beta` o `RC` cuenta como menor que la versión final.

**`yt_versions_pc.json`** (público, `…/api/yt_versions_pc.json`) es una copia estática que se regenera cuando el panel cambia algo en `pc_versions`:

```json
{"latest": {"id":3,"version_pc":"2.5.0","is_mandatory":false,"is_obsolete":false,
            "release_notes":"…","download_url":"…","date_added":"…"} | null,
 "obsolete_versions": ["…"], "versions": [ …todas, de mayor a menor id… ]}
```

Ahora mismo, en el repo: `{"latest": null, "obsolete_versions": [], "versions": []}`.

### 5.6 `api_messages.php`: comunicados

**Petición**: `GET …/api/api_messages.php?app=<id>`. `platform` se ignora.

- Android `MainActivity`: `?app=ytmusic&platform=android`.
- `RssFeedNotifyWork`: sin parámetros (el servidor asume `ytmusic`).

**Lógica del servidor**: `app` por defecto es `ytmusic` (con `strip_tags`). En **cada** llamada ejecuta `CREATE TABLE IF NOT EXISTS` y un `ALTER TABLE`, es decir, escribe. Devuelve los mensajes con `is_active = 1`, `target_app = 'all'` o igual a `app`, y `created_at` de **las últimas 24 h** (UTC), con `ORDER BY id DESC`.

```json
[{"id":12,"title":"…","message":"…","link":"…","target_app":"all","is_active":1,"created_at":"2026-10-16 09:00:00"}]
```

Si falla: **500** `{"error":"Database error"}`. Los valores de `target_app` que ofrece `messages.php` son `all`, `ytmusic` ("Solo YT Music Mod"), `ytmusicpc` ("Solo YT Music Mod PC") y `libretube` ("Solo YouTube Mod").

**Cómo lo interpreta cada app:**

- **Android `MainActivity.revisarComunicadoBackground`**: toma `[0]` y, si `id > AdminPrefs.last_admin_msg_id`, pone `hasUnreadAnnouncement = true`. **Nadie lee ese indicador y nadie llama a `onBellClicked()`**, así que el diálogo manual (`revisarComunicadoManual` → `mostrarComunicado`) es código muerto. Los comunicados llegan de verdad por `RssFeedNotifyWork`, a la lista de notificaciones.
- **iOS**: no lo usa.

### 5.7 `log_error.php` (+ `error_platform.php`): reporte de errores

**Petición**: `POST …/api/log_error.php` con `Content-Type: application/json` y un objeto JSON como cuerpo. El servidor lee como máximo **262 144 bytes**: un cuerpo más largo queda cortado, se convierte en JSON inválido y responde "Datos vacíos".

| Campo | Límite y valor por defecto en el servidor | Android (`CrashManager`) | iOS (`KuroCrashReporter`) | Recomendado para el escritorio |
|---|---|---|---|---|
| `app_name` | 200, `Desconocida` | `YT Music Mod` | `YT Music Mod` (`KuroPanel.APP_NAME`) | `YT Music Mod` (para agruparse con el resto) |
| `tipo` | 200, `Desconocido` | `FATAL_CRASH` | ver la tabla de abajo | ver la tabla de abajo |
| `mensaje` | 5000, `""` | `exception.message ?: "Sin mensaje"` | el mensaje | el mensaje |
| `stacktrace` | 30 000, `""` | `stackTraceToString()` completo | se recorta a 60 000 antes de enviar | ≤ 30 000 |
| `dispositivo` | 500, `""` | `"${MANUFACTURER} ${MODEL} (Android ${RELEASE})"` | `"Apple <uname.machine> (<systemName> <systemVersion>) ID <mac>"` | `"<os.name> <os.version> (<os.arch>) ID <mac>"` |
| `version_app` | 100, `""` | `BuildConfig.VERSION_NAME` | `"<versionName> (iOS)"` | `"<versionName> (Windows)"` / `"(macOS)"` |
| `platform` | alias → `plataforma` | `android` | `ios` | **`windows`** / **`macos`** |
| `mac` | 100, `""` | no se manda | `KuroDeviceId.mac()` | el `mac` del escritorio |

**Lo que hace el servidor**: crea la tabla `app_errores` y añade las columnas `app_name`, `plataforma` y `mac` si faltan. Inserta con `fecha = date('Y-m-d H:i:s')` en Panamá y `plataforma = error_platform_detect(platform, dispositivo, version_app)`. Conserva solo los **últimos 3000** errores. Responde **siempre HTTP 200**: `{"status":"success"}`, o `{"status":"error","message":"Datos vacíos"}` (JSON vacío o inválido), o `{"status":"error","message":"Error interno"}`.

**`error_platform_detect`**:

1. Alias de `platform`, en minúsculas y recortado:
   - `android`
   - `ios`, `iphone`, `ipad`, `ipados` → `ios`
   - `macos`, `mac`, `osx`, `darwin` → `macos`
   - `windows`, `win`, `win32`, `win64` → `windows`
   - `linux`
2. Si no hay alias, busca en el texto de `dispositivo` + `version_app`, en este orden: `ios|iphone|ipad|ipod` → `android` → `macos|mac os|os x|macbook|imac|mac mini|mac studio|darwin` → `windows` → `linux` → `desconocida`.

El monitor (`manager_errores.php`) cuenta los últimos 500 y muestra 100, con filtro por `android`, `ios`, `macos`, `windows`, `linux` y `desconocida`.

**Valores de `tipo` que usan las apps:**

| `tipo` | Plataforma | Cuándo |
|---|---|---|
| `FATAL_CRASH` | Android, iOS | Excepción de Kotlin no capturada |
| `FATAL_CRASH_OBJC` | iOS | `NSException` no capturada (`NSSetUncaughtExceptionHandler`) |
| `ARRANQUE_FALLIDO` | iOS | El arranque anterior murió sin excepción de Kotlin (crash nativo o watchdog); el mensaje lleva el último paso alcanzado |
| `LOGIN_RED` | iOS | Excepción en la petición de login |
| `LOGIN_RESPUESTA` | iOS | El login respondió algo que no es JSON (los primeros 2000 caracteres van en `stacktrace`) |
| `ERROR_REPRODUCCION` | iOS | Fallaron todas las fuentes de una canción (`AvPlayerAdapter.reportPlaybackFailure`) |
| `ERROR_DESCARGA` | iOS | No hay stream AAC descargable, o la descarga falló (`IosDownloadHandler`) |

**Cómo se envía:**

- **Android**: `CrashManager.init(versionName, url, "YT Music Mod")` es lo primero de `Application.onCreate`. En un crash, POST síncrono en el hilo que falló (3 s / 3 s) y después el manejador anterior. `logSilentError` existe, pero **nadie lo llama**. Si el crash ocurre en el **hilo principal**, Android lanza `NetworkOnMainThreadException` dentro de `sendErrorToServer`, el `catch` la traga y **el reporte no sale**: solo llegan los crashes de hilos secundarios.
- **iOS**:
  - `install()` es lo primero de `MainViewController.startOnce()`.
  - Envío normal: asíncrono por `NSURLSession` (timeout 4 s); si falla, va a la cola pendiente (`kuro.pending_crash_reports`, máximo 10), que se reenvía en el siguiente arranque.
  - Crash fatal: primero guarda en la cola, luego envía en modo bloqueante con un semáforo de 4 s y lo quita de la cola si llegó.
  - El arranque se marca con `kuro.launch_step` en los pasos `crash-reporter`, `koin`, `version-manager` y `ui`. La marca se borra 3 s después de mostrar el login o 5 s después de mostrar la app. Si sigue puesta en el siguiente arranque, se reporta `ARRANQUE_FALLIDO`.
  - `IosPlaybackDiagnostics` envía como máximo **15 reportes por sesión**.
  - "Entregado" significa HTTP 2xx. No se mira el `status` del cuerpo.

### 5.8 Lo que existe en el panel y estas apps no usan

- **`check_code.php`**: igual que `check_update.php`, pero lee la tabla `yt_versions` (que escribe `manager_up_now.php`, con APK en `api/updown/`) y además reescribe `yt_versions.json`. Parece ser el de la app "YouTube Mod". **AMBIGUO**: el nombre no tiene relación con códigos de acceso.
- **`login_bridge.php`**: recibe POST `username`, `password` y `mac` (o `MAC`); calcula el token **en el servidor** con `PLAYER_API_SECRET`; hace POST a `<PANEL_BASE>/api/player_api.php` por cURL con `X-Origin-Secret`, el User-Agent un User-Agent interno fijo y **sin verificar TLS**. Devuelve un JSON reducido: `{"user_info":{"auth","status","message"}}`. También acepta `1` / `0` como respuesta. Guarda un registro en `debug_log.txt` con las contraseñas enmascaradas, que se vacía al pasar de 2 MB.
- **`login_view.php`**: página HTML de login para apps WebView. Parámetros: `mac`, `username|user`, `password|pass`, y `check=1` para la validación silenciosa. Llama a `login_bridge.php` y redirige a `<ESQUEMA>://login_exitoso` o a `<ESQUEMA>://login_requerido?error=expired|blocked|not_found|denied`; la validación silenciosa siempre manda `denied`.
- **`algoritmo.php`**: `GET ?action=registrar&username=<u>&keyword=<artista>` suma un punto en `usuarios_log.json`. `?action=obtener&username=<u>` devuelve `{"recomendacion":"<top>"|"tendencias"}`. Cada 24 h la limpieza deja solo el primero por usuario.
- **`dns.php`**: devuelve en hex la lista `[{namex, urlx}]` de la tabla `dns` cifrada con AES-256-CBC. La clave son 16 hex aleatorios repetidos dos veces y va **al final de la respuesta, invertida**. El IV es fijo: `<IV_FIJO>` (en `api/dns.php`).
- **`versions.json`** (volcado de `app_versions`) y **`yt_versions.json`** (de `yt_versions`): copias estáticas públicas.

---

## 6. Estado local y ciclo de vida de la sesión

### 6.1 Claves guardadas

| Dato | Android | iOS |
|---|---|---|
| ¿Sesión iniciada? | `SharedPreferences("AppPrefs").isLoggedIn` (bool) | `NSUserDefaults["kuro.isLoggedIn"]` |
| Usuario | `AppPrefs.saved_user` (texto en claro) | `kuro.saved_user` |
| Contraseña | `AppPrefs.saved_pass` (**texto en claro**) | `kuro.saved_pass` (**texto en claro**, en UserDefaults) |
| Último OK del panel | `AppPrefs.sync_telemetry_timestamp` (long, ms) | `kuro.sync_telemetry_timestamp` (double, ms) |
| Último comunicado visto | `SharedPreferences("AdminPrefs").last_admin_msg_id` | — |
| ID de dispositivo | (se calcula cada vez) | Keychain `com.kuroboy56.kuromusic.device`/`device_id` + `kuro.device_id` |
| Reportes pendientes / paso de arranque | — | `kuro.pending_crash_reports`, `kuro.launch_step` |

### 6.2 Secuencia de arranque

**Android, en frío:**

1. `Application.onCreate`:
   1. `CrashManager.init`.
   2. `verificarFirma` (solo en release; §7).
   3. `validarCuentaSilenciosamente`: `chack_api.php` en segundo plano (§5.2).
   4. Sentry, Koin y el resto.
2. `LoginActivity` (el launcher): si `isLoggedIn`, pasa directamente a `MainActivity`; si no, muestra el formulario.
3. `MainActivity.onCreate`:
   1. `MediaAudioConfig.checkAudioRoute` (firma; §7).
   2. Si no hay `isLoggedIn`, vuelve a `LoginActivity`.
   3. Lanza `verificarDiasRestantes()` y `verificarActualizacionApp()`.
   4. En `setContent`: `revisarComunicadoBackground()` y la revalidación con `player_api.php` (§5.1).

   `MainActivity` se recrea al girar la pantalla (`orientation` no está en `configChanges`), al cambiar el idioma o el tema, y todo esto **se repite**.
4. Cada 24 h: `RssFeedNotifyWork` (actualización y comunicados).

En total, un arranque en frío con sesión hace 5 peticiones al panel: `chack_api`, `player_api`, `check_days`, `check_update` y `api_messages`.

**iOS:**

1. `MainViewController.startOnce()`:
   1. `KuroCrashReporter.install()` (con la marca de arranque y el envío de pendientes).
   2. `IosPlaybackDiagnostics.reporter` → `KuroCrashReporter.logSilentError`.
   3. Koin y `VersionManager`.
   4. Con `-kuroPanelSelfTest`, `KuroPanel.selfTest()`: un login con un usuario inexistente, días, actualización y el MAC, todo impreso como `KURO_SELFTEST …`.
2. `KuroGate { App() }`: si no hay sesión, `KuroLoginScreen`. Si la hay, `App()` + `KuroSessionChecks`, que lanza en paralelo:
   - `revalidate()`;
   - `daysLeftAlert()`;
   - `updateInfo()`, a los 600 ms;
   - `launchCompleted()`, a los 5 s.

   `KuroSessionChecks` vuelve a ejecutarse justo después de cada login: el login va seguido de una revalidación.

### 6.3 Formas de cerrar la sesión

| Disparador | Android | iOS |
|---|---|---|
| Panel con 401/403 en la revalidación | `MainActivity`: `AppPrefs.clear()` (borra todo) y `LoginActivity` | `logout()` |
| `auth != 1` o `status` inválido en la revalidación | `MainActivity`: `clear()` y `LoginActivity`. `Application` (`chack_api`): `expulsarUsuario` | `logout()` |
| Más de 7 días sin un OK del panel | Solo en el camino `chack_api`: `expulsarUsuario` | `checkOfflineTime` → `logout()` |
| 200 que no es JSON | `MainActivity`: **crash** | Cuenta como offline |

- `expulsarUsuario`: `isLoggedIn=false` y la marca de tiempo a 0; conserva usuario y contraseña; abre `LoginActivity` y mata el proceso.
- `logout()` (iOS): `isLoggedIn=false`; borra **solo la contraseña**; marca de tiempo a 0; pausa el reproductor; la puerta vuelve al login sin matar el proceso.

Tolerancia offline: **7 días = 604 800 000 ms** desde `sync_telemetry_timestamp`, que es el último OK del panel o, si no había ninguno, el primer fallo de red.

---

## 7. Comprobaciones de integridad locales (el "guardián")

No hablan con el panel, pero bloquean la app:

1. **`SimpMusicApplication.verificarFirma`**: solo en release. Calcula `Base64(SHA-256(certificado del primer firmante))` y lo compara con `VALID_SIGNATURE_HASH` descifrado (esquema B). Si no coincide o hay una excepción: `killProcess` + `exitProcess(0)`. En debug solo escribe el hash en el log (`FIRMA_APP_DEV`).
2. **`MediaAudioConfig.checkAudioRoute`** (lo primero de `MainActivity.onCreate`; el nombre despista a propósito): calcula el SHA-256 en hex mayúsculas de cada firmante y lo compara con dos huellas fijas, la de desarrollo y la oficial, escritas como enteros binarios ASCII. Si no coincide: `finishAffinity()` + `exitProcess(0)`.
3. **iOS**: ninguna, a propósito. Quien instala el IPA (Sideloadly, AltStore) vuelve a firmarlo.
4. **Escritorio**: no hace falta nada equivalente. El panel no verifica al cliente: el token es lo único que se le parece, y su secreto viaja dentro de la app.

---

## 8. Qué configura cada página del panel

| Página del panel | Escribe | Lo lee |
|---|---|---|
| `manager.php` (Actualizaciones YT Music) | tabla `app_versions` + `api/versions.json`. Solo admite `.apk`, lo guarda en `api/update/` y borra los APK anteriores. URL: `<DOWNLOAD_BASE>/api/update/<ts>_<nombre>.apk` | `check_update.php` (Android e iOS) |
| `manager_up_now.php` | tabla `yt_versions`, APK en `api/updown/`. URL: `<DOWNLOAD_BASE>/api/updown/…` | `check_code.php` (no lo usan estas apps) |
| `desk_up_manager.php` (Actualizaciones PC) | tabla `pc_versions` + `api/yt_versions_pc.json`. Elige un archivo ya subido a `api/updown_pc/` (`.exe`, `.msi` o `.zip`). Versión en texto, obligatoria y obsoleta | `desktop_update.php`, `yt_versions_pc.json` |
| `manager_days.php` | `api/config_days.json` (`title_3..0`, `msg_3..0`) | `check_days.php` |
| `messages.php` | tabla `app_messages` (`title`, `message`, `link`, `target_app`, `is_active`) | `api_messages.php` (solo durante 24 h) |
| `downloads.php` | `api/config_downloads.json` (enlaces `pc_music`, `mac_music`, `mobile_*`, …; solo el dueño los edita) | Solo el panel. Ninguna API lo sirve y el `.htaccess` lo bloquea |
| `adduser.php` | `menualuser`: `username`, `password`, `expire_date`, `quota` (huecos de dispositivo libres, por defecto 5), `mac_address` (MACs separados por comas), `blok`. Bloquear = `blok=1` y 20 años menos en la caducidad. Reiniciar MACs = `mac_address=''` y se recupera la cuota | `player_api.php`, `check_api.php`, `check_days.php` |
| `manager_errores.php` | — (lee `api/errores.db`) | Monitor de errores |

---

## 9. Checklist para el port a Windows y macOS

Objetivo: que el escritorio se comporte como iOS, que es el port más limpio, y no herede los fallos de Android.

1. **Cliente HTTP propio para el panel.** HTTPS a la misma base, `.php` explícito, sin almacén de cookies, **sin** `X-Origin-Secret`. Ponle un User-Agent propio (p. ej. `YTMusicMod-Desktop/<versión> (Windows)`): el `Java/<versión>` por defecto es justo el tipo de agente que una regla anti-bots de Cloudflare puede rechazar con 403, y un 403 cierra la sesión (punto 7). Timeouts como en iOS: conexión 8 s, petición 10 s.
2. **Reutilizar la lógica de `KuroPanel.kt`.** El token, el formato del MAC, la interpretación de cada respuesta y los mensajes no dependen de la plataforma. Lo propio de cada plataforma es el almacenamiento (`NSUserDefaults`), el motor HTTP (`Darwin`), el ID de dispositivo y la UI. Lo más corto es mover esa lógica a código compartido y dejar las cuatro piezas propias detrás de `expect`/`actual`, o copiar `KuroPanel` en `jvmMain` cambiando solo esas cuatro piezas. Mismas URLs ofuscadas (esquema A) y mismo `SECRET_KEY`.
3. **ID de dispositivo (`mac`), estable entre reinstalaciones**:
   - **Windows**: `HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid` (REG_SZ, GUID v4 aleatorio). Se lee sin ser administrador; sobrevive a reinstalar la app y cambia al reinstalar Windows. Léelo en la vista de registro de **64 bits**: desde un proceso de 32 bits sin `KEY_WOW64_64KEY` da "no encontrado". JNA ya es dependencia (`Advapi32Util.registryGetStringValue`); también vale `reg query`. **No uses el UUID SMBIOS** (`Win32_ComputerSystemProduct`): en los Dell empieza por `4C4C4544-00…` y los primeros 16 hex coinciden entre equipos, y algunos OEM ponen `FFFFFFFF-…`.
   - **macOS**: `IOPlatformUUID` (`ioreg -rd1 -c IOPlatformExpertDevice`, o IOKit por JNA). Está ligado al hardware y sobrevive a reinstalar la app y el sistema.
   - Mismo procesamiento que iOS: quitar `-`, mayúsculas, `trimStart('0')`, respaldo `1A2B3C4D5E6F7A8B` si queda vacío, `padEnd(16,'A')`, `take(16)`, pares con `:`.
   - **Guárdalo** al calcularlo por primera vez (p. ej. `~/.simpmusic/kuro_device_id`, la carpeta de datos que el escritorio ya usa para DataStore y Room) y desde entonces usa el valor guardado, como hace iOS con el Keychain. Si no se puede leer la fuente, genera un UUID aleatorio una vez y guárdalo.
   - Cada ID nuevo gasta una unidad de `quota` en el servidor. Un ID que cambia acaba con el mensaje `Superó el límite de dispositivos disponibles`.
4. **Token**: §4 tal cual, con `ZoneId.of("America/Panama")` y `Locale.ROOT`. Mándalo siempre.
5. **Pantalla de login** (como `KuroLoginScreen`): recortar los campos; si alguno está vacío, `Por favor, complete los campos`; `player_api.php` sin reintentos; mensajes de error como `denyMessage` de iOS, no como Android, que siempre enseña "Disabled". Si va bien, guarda `isLoggedIn`, `saved_user`, `saved_pass` y **`sync_telemetry_timestamp = ahora`**. Rodea `App()` con una puerta como `KuroGate`, en `DesktopApp.kt` / `runDesktopApp`.
6. **Persistencia de la sesión**: en `~/.simpmusic/` (o en `java.util.prefs`). La contraseña hace falta para revalidar: Android e iOS la guardan en claro, así que hacer lo mismo da paridad. Si se quiere mejorar, el llavero del sistema (Credential Manager / Keychain).
7. **Revalidación**: al arrancar, `player_api.php` con 3 intentos separados 2 s.
   - 401/403 → logout.
   - Sin respuesta, o 200 que no es JSON → tolerancia offline de 7 días.
   - `auth == 1` (de `user_info.auth`, o de `auth` en la raíz) y `status` ∉ {Expired, Banned, Disabled} → renueva la marca de tiempo.
   - Cualquier otra cosa → logout.
   - Logout = el de iOS (`isLoggedIn=false`, borrar la contraseña, marca a 0, pausar el reproductor, volver al login), **no** el `killProcess` de Android.
   - **Recomendación que se aparta de los móviles**: el escritorio puede pasar días abierto en la bandeja, así que conviene revalidar también cada 6–12 h y al volver de la suspensión. Si no, una cuenta caducada sigue funcionando hasta que se reinicia la app.
8. **No cerrar la sesión por un fallo de reloj** (también se aparta del móvil, y es opcional): ante `Conexión rechazada: Protocolo de seguridad fallido.`, en lugar de cerrar la sesión, reintentar una vez con la hora de `server_info.timestamp_now`, que viene incluso en la denegación, o al empezar la hora siguiente. Evita expulsar a alguien porque la petición cruzó el cambio de hora o el reloj del PC va mal.
9. **Aviso de días**: `check_days.php?username=…&mac=…&token=…` y mostrarlo con la regla de iOS (`status == success`, título no vacío, `days_left` entre −1 y 3). No bloquea ni cierra la sesión.
10. **Actualizaciones**: `desktop_update.php?version_pc=<BuildKonfig.versionName>`.
    - Si `update_available`, mostrar `changelog`.
    - Obligatoria = `is_mandatory` **o** la versión local está en `obsolete_versions`. Si es obligatoria, el diálogo no se cierra.
    - **Comprobar que `download_url` es del sistema** (Windows: `.exe` o `.msi`; macOS: `.dmg`, `.pkg` o `.zip`) y, si no lo es, no ofrecer nada. Es la misma regla con la que iOS ignora los `.apk`, y evita que un instalador de Windows bloquee a los usuarios de Mac.
    - `ACTUALIZAR` abre el navegador, o descarga y lanza el instalador.
    - Respaldo si el endpoint falla: `yt_versions_pc.json` (`latest.version_pc` comparado en el cliente). **No** uses `check_update.php`: es para el APK.
11. **Comunicados** (opcional, no hay equivalente funcional en los móviles): `api_messages.php?app=ytmusicpc`, que devuelve `all` + "Solo YT Music Mod PC". Mostrar el de mayor `id` si es mayor que el último visto (guárdalo como `last_admin_msg_id`). Solo vive 24 h en el servidor.
12. **Reporte de errores** (como `KuroCrashReporter`):
    - POST JSON a `log_error.php` con `platform` = **`windows`** o **`macos`**, `app_name` = `YT Music Mod`, `version_app` = `"<ver> (Windows)"` / `"(macOS)"`, `dispositivo` con `ID <mac>`, y `mac`.
    - `Thread.setDefaultUncaughtExceptionHandler` encadenado con el que ya tengan `CrashDialog` y Sentry.
    - Envío en segundo plano, nunca en el EDT, con 4 s de timeout. Cola pendiente en disco, como mucho 10, y reenvío en el siguiente arranque.
    - **Marca de arranque → `ARRANQUE_FALLIDO`**. En escritorio esto pesa: un crash nativo en libmpv o JNA tumba la JVM sin pasar por Kotlin.
    - Conectar `pushPlayerError` a `ERROR_REPRODUCCION`, con un máximo de 15 por sesión.
    - Cuerpo por debajo de 256 KiB y `stacktrace` de hasta 30 000.
    - Para dar un reporte por entregado, mirar `status == "success"` en el cuerpo, no solo el 2xx.
13. **Autotest**: un flag como `-kuroPanelSelfTest` que llame a cada endpoint e imprima una línea por llamada. Sirve para probarlo en CI.
14. **No portar**: las comprobaciones de firma de Android, el camino `chack_api.php` y el diálogo de comunicados muerto.
15. **Cambios en el panel para que el escritorio quede completo**:
    - una columna de plataforma en `pc_versions`, o una tabla por sistema;
    - admitir `.dmg` y `.pkg` en `desk_up_manager.php`;
    - incluir `version_pc` en la respuesta de `desktop_update.php`.

---

## 10. Riesgos y errores que se ven en el código

1. **`chack_api.php`** (§5.2): si en producción da 404, todos los usuarios de Android tienen que volver a iniciar sesión cada 7 días. **VERIFICAR.**
2. **El cambio de hora tumba sesiones.** El token solo vale en la hora en curso. Una petición generada a las hh:59:59 que llega a las hh+1 recibe `auth=0`, y las dos apps lo tratan como un rechazo real: Android borra las credenciales, iOS cierra la sesión. Lo mismo pasa con un reloj de dispositivo desviado una hora o más: nunca puede iniciar sesión.
3. **Android se cuelga con un 200 que no es JSON** en la revalidación de `MainActivity`: `JSONObject` sin `try` dentro de un `LaunchedEffect`. Pasaría con la pantalla HTML de licencia, una avería de PHP con warnings delante del JSON, o una página intermedia con 200.
4. **Android enseña "Disabled"** como error en cualquier login fallido, porque lee `status` en lugar de `message`.
5. **Android pierde los crashes del hilo principal**: `NetworkOnMainThreadException` dentro de `CrashManager`, capturada en silencio.
6. **El token no es obligatorio** (Fase 1): sin token, la petición se acepta. Además el secreto está en todas las apps, así que el token no protege frente a quien tenga la app. Si se activa la Fase 2, las apps sin token dejan de funcionar.
7. **La contraseña viaja en la query string** (GET), así que queda en los registros de Cloudflare y del servidor, y `player_api.php` la devuelve en claro en la respuesta. Las apps la guardan en claro. Para el escritorio, POST form-urlencoded funciona igual y la saca de la URL.
8. **Un 401/403 se toma como cuenta rechazada.** Cualquier 403 de Cloudflare (WAF, anti-bots, o una petición que llega al origen sin `X-Origin-Secret`) cierra la sesión en las dos apps. En Android además borra todo `AppPrefs`.
9. **Una cuenta bloqueada se ve como "Expired"** en `player_api.php` (por los −20 años), y Android no muestra el aviso de bloqueo (−1 queda fuera de 0..3).
10. **El registro de dispositivo no es atómico** (dos `UPDATE` separados): con dos logins simultáneos desde dispositivos nuevos, la cuota puede gastarse mal.
11. **`check_days.php` no autentica**: cualquiera que conozca un nombre de usuario puede saber si existe y cuántos días le quedan.
12. **iOS nunca ofrece actualizaciones** con el panel actual: no hay `ios_download_url`, y `manager.php` solo admite `.apk`.
13. **`desktop_update.php`** toma la última fila insertada, no la versión más alta; tiene una sola URL para todos los sistemas de escritorio y no devuelve el nombre de la versión.
14. **`ob_clean()` sin buffer** en `check_days.php` y `desktop_update.php`: si el servidor tuviera `display_errors` activo, el aviso de PHP rompería el JSON. **AMBIGUO** (depende de `php.ini`).
15. **`api_messages.php` escribe en la BD** (`CREATE` / `ALTER`) en cada lectura, y el modo de login por código (`codes`) apunta a una tabla que no existe en la BD del repo.
16. **Android revalida al rotar**: `MainActivity` se recrea, repite todas las llamadas y puede volver a mostrar el aviso de días.

## 11. Pendiente de verificar en el servidor real

- Que `api/chack_api.php` exista o no (y lo que responda).
- Que `api/` quede o no protegida por `X-Origin-Secret` en el origen (herencia de `mod_rewrite`), y qué reglas anti-bots o WAF tiene Cloudflare para User-Agents que no son de navegador.
- Si la tabla `codes` existe en producción.
- Que `display_errors` esté desactivado (punto 14).
- El contenido real de `pc_versions` / `yt_versions_pc.json` y qué `version_pc` usa hoy la app de PC que ya existe ("YT Music Mod PC", MSI 1.5.0 según `downloads.php`).
