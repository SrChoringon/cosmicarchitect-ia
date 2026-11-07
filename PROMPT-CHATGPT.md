# Planos de gran sala cerrada para ChatGPT — CosmicArchitect 0.2

Copia el texto siguiente en ChatGPT y añade tu descripción. Guarda solo el YAML
resultante en `plugins/CosmicArchitect/plans/mi_sala.yml`. Ejecuta
`/cosmic check mi_sala` y `/cosmic build mi_sala`. No hace falta reiniciar.

```text
Genera exclusivamente un plano YAML válido para CosmicArchitect 0.2, sin
comandos, código ni campos inventados. Quiero una sola sala cósmica enorme,
cerrada, decorada y monumental. El generador tiene un arquetipo cathedral:

version: 1
name: Catedral del Eclipse
palette: void
seed: 91347
rooms:
  - {id: catedral, type: cathedral, x: 0, z: 0, radius: 40, height: 48}
links: []

Reglas obligatorias:
- Mantén una sola sala, type: cathedral y links: [].
- radius entero 32..48. El ancho es 2*radius+1; el largo, ancho+48.
- height entero 36..52: altura máxima de la bóveda sobre el suelo.
- x y z: enteros -3..3; preferiblemente ambos 0.
- id: letras a-z, números, guion o guion bajo; de 1 a 32 caracteres.
- name: texto de hasta 100 caracteres. Usa un nombre sin caracteres YAML especiales.
- palette solo admite void, nebula o eclipse. Usa void para amatista con piedra
  oscura, eclipse para detalles ámbar o nebula para acentos magenta. La envolvente
  de la catedral siempre es opaca y oscura; la paleta cambia algunos acentos.
- seed: entero. Cambia detalles de textura y distribución de cristales pequeños.
- No hay parámetros para mover los pilares ni inventar nuevas esculturas.
- No admite enemigos, jefes, trampas, botín, pisos múltiples ni scripts.
- Interpreta mi descripción dentro de estos límites. El nombre no cambia la
  arquitectura y la temática no añade automáticamente materiales o funciones.

Mi descripción: [ESCRIBE AQUÍ]
```

El tipo `cathedral` genera una nave con capillas, bóveda, columnas, puerta
monumental y altar de eclipse. Esta versión tiene esa arquitectura definida;
ChatGPT elige los parámetros admitidos, no programa formas arbitrarias.
Los cinco arquetipos pequeños de 0.1 siguen funcionando, pero este prompt
pide específicamente la nueva gran sala cerrada.

La transferencia desde tu suscripción de ChatGPT es manual. No uses cookies,
contraseñas ni claves de tu cuenta en el plugin.
