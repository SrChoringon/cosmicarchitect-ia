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
