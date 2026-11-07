# Validación de CosmicArchitect 0.2.0

Entorno: Java 21.0.13, Paper 1.21.11 build 132, MythicDungeons 2.1.0,
sin jugadores, ligado a 127.0.0.1, heap máximo de servidor 2 GB. No es Vultam.

## Nueva gran sala cerrada

- Compilación correcta contra la misma API Paper fijada en el proyecto.
- 35 726 comprobaciones de geometría con heap máximo de 512 MB: correctas.
  El número incluye verificaciones por celda del suelo, techo y muros.
- Catedral del ejemplo: 96 652 posiciones. El recuento incluye las posiciones
  de aire que se despejan en la entrada; no es una medición de memoria.
- Cada celda de la planta tiene suelo y una cubierta opaca. Los muros exteriores
  están cerrados; menos del 1 % de posiciones usan cristal.
- Pasillo central despejado desde la entrada hasta el frente del altar.
  Las tres filas de escaleras del altar están orientadas hacia el sur.
- Radios 32 y 48, alturas 36 y 52 y coordenadas negativas: dentro del presupuesto
  de 250 000 posiciones y con punto de entrada despejado.
- Se rechazan catedrales con tamaño inválido o mezcladas con otras salas.
- Prueba de construcción completa con el JAR original de MythicDungeons
  instalado y activo. Un complemento de auditoría separado verificó todas las
  96 652 posiciones y sus estados de bloque contra el plano calculado.
- El punto de entrada está en (0, 81, -52). No quedan tickets de chunks del
  constructor al finalizar.
- El nuevo plano se instaló al actualizar sin borrar eclipse.yml ni worlds.yml.

La vista técnica en docs/catedral-interior.png se genera desde el volcado real
de geometría, usando tools/render_geometry.py (requiere NumPy y Pillow).
Sus colores, iluminación y formas parciales de bloque son aproximados: no es
una captura de Minecraft y no valida el aspecto o nivel de luz real en el cliente.

El complemento de auditoría no se distribuye. Siguen apareciendo avisos de red
ajenos al constructor en las consultas de versiones/autenticación de Paper.
Los cinco avisos de compilación de constantes GameRule obsoletas siguen presentes.

Pendiente: recorrido con cliente Minecraft, luz real con tus ajustes y paquetes,
teletransportes con jugadores, concurrencia, TPS/MSPT y memoria en Vultam.
No hay integración de partidas ni optimización interna de MythicDungeons.

---

## Registro histórico: 0.1.0

# Validación del prototipo 0.1.0

Entorno de prueba: Java 21.0.13, Paper 1.21.11 build 132, sin jugadores;
servidor ligado a 127.0.0.1 y heap máximo de 2 GB. No es el hosting Vultam.

- Compilación contra Paper API `1.21.11-R0.1-20260511.115010-91`: correcta.
  La API informa cinco avisos por constantes GameRule obsoletas; siguen
  funcionando en la versión objetivo.
- 50 comprobaciones de geometría con heap máximo de 512 MB: correctas.
  Incluyen conectividad caminable entre centros, determinismo de semilla,
  variación de paleta, límites, planos desconectados, IDs/conexiones duplicados,
  coordenadas negativas, cinco arquetipos y dimensiones mínimas/máximas.
- Ejemplo de cinco salas: 29 857 posiciones; incluye aire de los pasos.
  No representa 29 857 bloques sólidos ni una medición de memoria.
- Plano de diez bibliotecas grandes: 107 905 posiciones dentro del límite.
- Construcción completa de la escena de ejemplo: aproximadamente 14 segundos
  con configuración inicial. El tiempo no predice el rendimiento con jugadores.
- Arranque simultáneo con el JAR original MythicDungeons 2.1.0: correcto.
- Tras apagar y reiniciar normalmente, un complemento de prueba separado
  comparó las 29 857 posiciones persistidas con el plano: coincidieron todas.
- Spawn a altura 81 y ausencia de tickets de chunk del constructor al terminar:
  correctos. Descarga del mundo con guardado: correcta.
- Nueva construcción cancelada durante la colocación: liberó su ticket de chunk
  y permitió descargar el mundo parcial. El registro conservó el estado cancelled.
- Nombre de plano con intento de salir de carpeta: rechazado.
- Intento de descargar el mundo principal: rechazado.

El complemento SmokeAudit se usó solo para pruebas y no está en el JAR final.
La prueba local produjo avisos de red de Paper/Mojang al intentar consultar
autenticación y versiones; no impidieron el arranque ni las pruebas descritas.

Pendiente: recorrido visual con un cliente Minecraft, comandos de teletransporte
con jugadores, carga concurrente real, plugins habituales del hosting y perfilado
de TPS/MSPT y memoria. No hay integración de partidas con MythicDungeons ni
optimización interna del JAR original en esta entrega.
