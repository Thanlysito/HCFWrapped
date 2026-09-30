# HCF Wrapped (en español)

"Spotify Wrapped" para mapas de HCF en **Paper/Spigot 1.8.8** (Java 8). Probado con Lunar Client 1.8.9.

## ⚠️ Importante: para compartir la tarjeta hace falta un webhook de Discord
`/wrapped share` dibuja la tarjeta del jugador y **la publica en un canal de Discord con un webhook**. Así es como el jugador recibe su imagen.

- **Con webhook** → la tarjeta sale en el canal de Discord ("¡**Jugador** compartió su HCF Wrapped!") y el jugador la puede descargar y compartir.
- **Sin webhook** → la imagen solo se guarda en el servidor, en `plugins/HCFWrapped/cards/<jugador>.png`. **El jugador no la puede ver** (solo el dueño del servidor).

Cómo configurarlo (1 minuto):
1. En Discord: **Ajustes del canal → Integraciones → Webhooks → Nuevo webhook → Copiar URL**.
2. Pégala en `plugins/HCFWrapped/config.yml`:
   ```yaml
   discord:
     webhook-url: "https://discord.com/api/webhooks/..."
   ```
3. `/wrapped reload`

Todo lo demás (menú, resumen en el chat, tops y títulos) funciona sin Discord.

## Qué trae
- **Menú de cofre** (`/wrapped`): tu cabeza con tu título, cada estadística como ítem (al pasar el mouse: tu valor, tu puesto, el promedio del servidor y una barra), cabezas de tu víctima favorita y tu némesis, y botones para ver el top o compartir. Las stats donde estás en el top 3 brillan.
- **Menú de top** (`/wrapped top`): las 10 mejores cabezas, tu puesto y una fila para cambiar de estadística. Clic en una cabeza = ver el Wrapped de ese jugador.
- **Resumen en el chat** (`/wrapped chat`): centrado, en dos columnas, con detalle al pasar el mouse y botones [MENU] [TOP] [COMPARTIR].
- **Título divertido** según en qué destacas (Adicto a las Pots, Maestro del Pearl, El Inmortal, Carne de Cañón...).
- **Tarjeta** (`/wrapped share`): cuerpo completo del skin, números grandes, color y barra por estadística, puestos en el top y caras de tu víctima y tu némesis.
- **Estadísticas de tu core** (KOTH, raideos...) con `/wrapped add <jugador> <stat> <cantidad>`.
- Español e inglés, todos los textos editables.

## Opciones de config.yml
- `language` (`en`/`es`): idioma. `server-name`: nombre en la tarjeta.
- `view-during-season` (`false`): si los jugadores pueden ver su Wrapped durante el mapa.
- `open-menu` (`true`): `/wrapped` abre el menú; en `false` muestra el chat.
- `remind-on-join` (`true`): al terminar la temporada, recordar `/wrapped` al entrar.
- `share-cooldown-seconds` (`300`): espera entre `/wrapped share`.
- `custom-stats`: estadísticas extra (`koth`, `raids`...) que se suman con `/wrapped add`.
- `discord.webhook-url`: **necesario para compartir tarjetas**. `discord.recap-on-end`: publicar el top 3 al terminar.
- `card.*`: colores de la tarjeta y de dónde sacar la cara (`avatar-url`) y el cuerpo (`body-url`) del skin.

Todos los textos están en `lang/es.yml` y `lang/en.yml` (UTF-8, colores con `&`). Después de editar: `/wrapped reload`.

## Compilar
1. Encender una vez **Paper 1.8.8** con **Java 8** (Temurin 8).
2. Copiar `Servidor18\cache\patched_1.8.8.jar` a `HCFWrapped\libs\`.
3. En PowerShell, dentro de esta carpeta: `.\gradlew build` → `build\libs\`.

## Probar sin otro jugador
En `Servidor18\bot\` hay un bot (Node.js + mineflayer) para probar kills y muertes: poner `online-mode=false` en `server.properties`, abrir `iniciar-bot.bat`, y en el chat del juego usar `!pelea`, `!quieto` o `!ven`. Al terminar, volver a `online-mode=true`. En modo local el OP se da con `deop` + `op` estando conectado.

## Archivos (src/main/java/dev/thanly/wrapped)
- `HCFWrapped.java` — clase principal (lee config e idiomas en UTF-8).
- `StatsManager.java` — temporada actual, guardar/leer, tops y promedios.
- `PlayerStats.java` — las estadísticas de un jugador.
- `StatTracker.java` — escucha kills, daño, pots, pearls, gapples, diamantes y tiempo jugado.
- `StatView.java` — icono, color, símbolo y detalle (puesto, promedio, barra) de cada estadística.
- `WrappedMenu.java` — menús de cofre (resumen y top).
- `ChatFormat.java` — centrar y alinear columnas en el chat.
- `Titles.java` — elige el título divertido.
- `CardData.java` / `CardRenderer.java` — arma y dibuja la tarjeta PNG (Java AWT).
- `DiscordWebhook.java` — publica en Discord.
- `WrappedCommand.java` — el comando /wrapped.
- `Messages.java` — textos en en/es con colores &.
