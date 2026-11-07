# CosmicArchitect 0.2.0 — prototipo para Paper 1.21.11 / Java 21

Constructor independiente de arquitectura cósmica a partir de planos YAML.
Genera los bloques desde cero mediante seis arquetipos procedurales.
Puede convivir con MythicDungeons, pero esta versión **no importa ni registra
dungeons en MythicDungeons**, no modifica su JAR ni sus configuraciones.
No es todavía un sistema de partidas, jefes, objetivos o recompensas.

## Primera prueba en el panel de Vultam

1. Detén el servidor de pruebas.
2. Retira el JAR anterior de CosmicArchitect y sube `CosmicArchitect-0.2.0.jar` a `plugins/`. Debe quedar solo una versión. Conserva MythicDungeons si lo usas.
3. Arranca con Java 21 y Paper 1.21.11. No requiere librerías adicionales.
4. Como OP, ejecuta `/cosmic check catedral`. El ejemplo se instala automáticamente.
5. Ejecuta `/cosmic build catedral` y consulta `/cosmic status`.
6. Al terminar verás `/cosmic visit cosmic_<identificador>`; copia ese comando.
7. Recorre la construcción. Regresa con `/cosmic leave`.

En consola, omite `/`. El permiso `cosmic.admin` es solo para OP por defecto.
La construcción crea un mundo vacío nuevo, con noche fija y dificultad pacífica.
No cambia el mundo en el que estabas ni el modo de juego de los jugadores.
La visita es una inspección física: no hay partida de dungeon ni protección
contra editar bloques. Evita dar el permiso a visitantes en producción.

## El ejemplo nuevo: Catedral del Eclipse

Una sola sala cerrada de 81 × 129 bloques, bóveda de 48 de altura sobre el suelo,
muros y techo opacos, pilares con capiteles, nervaduras, cornisas, capillas,
bibliotecas, estatuas, mosaico y altar escalonado. Una esfera eclipsada con
anillos ocupa el espacio sobre el altar. Hay luces cubiertas por alfombras,
lámparas colgantes y velas; no hay filas de cubos luminosos en la bóveda.

El plano `catedral.yml` se instala automáticamente en `plans/` al actualizar.
Los archivos existentes no se sobrescriben. El antiguo `eclipse.yml` mantiene
las cinco salas pequeñas de cristal de 0.1: no es el ejemplo nuevo.
La actualización no altera mundos ya construidos. Genera un mundo nuevo.

## Nuevas descripciones

Usa el texto de `PROMPT-CHATGPT.md` aquí en ChatGPT. Guarda el resultado en
`plugins/CosmicArchitect/plans/<nombre>.yml` y usa los comandos check/build.
Los nombres de archivo deben tener 1..40 caracteres a-z, 0-9, guion o guion bajo.
El comando recibe el nombre sin `.yml`. No es necesario reiniciar para importar planos.

Los planos se validan: hasta 64 KB, diez salas, conexiones entre vecinas,
tipos antiguos con radios 9..16 y alturas 10..20, y hasta 250 000 posiciones.
El tipo nuevo `cathedral` usa radio 32..48 y altura 36..52, una sola sala por
plano, `links: []`; su largo es el ancho más 48 bloques.
Solo se permiten los materiales de las tres paletas incluidas.
El esquema no acepta scripts, comandos de consola ni URLs.

## Rendimiento y RAM

La geometría se calcula fuera del hilo principal; los bloques se colocan en
el hilo del servidor con carga asíncrona de chunks. Se procesa un chunk a la vez
y se retira su ticket cuando termina. Una sola construcción simultánea.

Configuración inicial: hasta 300 posiciones por tick y 3 ms de colocación por tick.
Ambos límites se aplican; el primero que se alcanza termina la tanda. Son un
presupuesto cooperativo, no un límite estricto para cada llamada de Bukkit.
Crear/cargar mundos, preparar datos y el guardado normal del servidor pueden
provocar pausas adicionales. No se puede garantizar cero lag.

Con 7 GB, mantén esos límites iniciales y prueba con los jugadores y plugins
habituales. CPU y GPU anunciadas como ilimitadas no garantizan rendimiento;
este flujo no usa GPU ni ejecuta un modelo local. No asignes más memoria por
encima del límite del hosting. Evita acumular mundos cargados: por defecto se
permiten dos mundos propios en memoria. Sal de uno con todos los jugadores y
usa `/cosmic unload <mundo>` para guardarlo y descargarlo de memoria. No borra
su carpeta y se puede volver a visitar.

Para bajar carga: cambia `blocks-per-tick` a 100 y `milliseconds-per-tick` a 2
en `plugins/CosmicArchitect/config.yml`; reinicia. La construcción tardará más.

## Cancelación, reinicios y limpieza

`/cosmic cancel` detiene el trabajo, libera el chunk y conserva el mundo parcial.
No hay reanudación ni deshacer sobre un mapa ajeno. Cada build crea otro mundo.
`/cosmic list` muestra planos y mundos; el estado se guarda en `worlds.yml`.
Solo los mundos con estado `complete` se pueden visitar por comando.
No edites worlds.yml para marcar como completa una construcción parcial.

Después de un reinicio, el primer visit vuelve a cargar el mundo completo.
Guarda y detén normalmente el servidor para persistir los chunks.
Una interrupción brusca puede dejar un mundo incompleto aunque el registro
diga building; vuelve a generar otro y elimina el parcial cuando ya no lo necesites.

Para eliminar una prueba: sal con todos los jugadores de ese mundo, detén el
servidor y elimina **solo** la carpeta `cosmic_<identificador>` correspondiente,
su entrada en `plugins/CosmicArchitect/worlds.yml` y, si Paper las creó,
sus carpetas hermanas `_nether`/`_the_end`. Confirma el identificador antes.
No borres la carpeta principal `world` ni las carpetas de MythicDungeons.

## Límites conocidos

- La calidad depende de los arquetipos admitidos y de sus parámetros; el texto
  no genera formas ilimitadas. La catedral usa una sola nave con altar escalonado; los tipos antiguos usan
  un nivel y puentes rectos.
- No conecta ChatGPT al juego. La transferencia del YAML es manual.
- No modifica ni optimiza internamente MythicDungeons. Para medir sus cuellos de
  botella hace falta perfilarlo con tus dungeons y carga real.
- No se ha validado convivencia con todos tus plugins ni rendimiento en Vultam.
- Las visitas con jugadores, retorno tras desconexión y aspecto en cliente
  requieren la prueba visual dentro de Minecraft.

## Código fuente y compilación

En el ZIP está en `source/`; en GitHub, en la raíz del repositorio. Requiere Python 3, Java 21 y conexión HTTPS a Maven Central
y al repositorio de Paper. Ejecuta `python3 build.py`. Descarga ECJ como compilador,
resuelve dependencias fijadas y ejecuta las pruebas de geometría, incluyendo el cierre de la catedral.
Las dependencias son de compilación; no se empaquetan ni sustituyen Paper.
El JAR original de MythicDungeons no se redistribuye en este paquete.
