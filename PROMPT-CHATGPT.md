# Crear un plano nuevo con tu suscripción de ChatGPT

Copia el siguiente texto en ChatGPT y añade tu descripción al final. Guarda solo
el contenido YAML de la respuesta en un archivo como `mi_dungeon.yml`.
Súbelo al panel del hosting en `plugins/CosmicArchitect/plans/`.
Ejecuta `/cosmic check mi_dungeon` y después `/cosmic build mi_dungeon`.

```text
Diseña una distribución para CosmicArchitect 0.1, Minecraft Paper 1.21.11.
Devuelve exclusivamente YAML válido, sin comandos, código ni explicaciones.
El constructor admite estas opciones; no inventes campos ni mecánicas:

version: 1
name: Texto de hasta 100 caracteres
palette: void
seed: 412778
rooms:
  - {id: entrada, type: gateway, x: 0, z: 0, radius: 12, height: 14}
links: []

Reglas:
- Entre 1 y 10 salas; cada celda x,z es única y sus valores son enteros entre -3 y 3.
- La distancia entre centros es 48 bloques por celda; todas las salas tienen el piso en Y=80.
- IDs únicos con letras minúsculas a-z, números, guion o guion bajo; hasta 32 caracteres.
- Cada sala tiene id, type, x, z, radius (entero 9..16), height (entero 10..20).
- type solo admite gateway (umbral rectangular con arco), library (biblioteca rectangular),
  observatory (cúpula con órbita), sanctum (santuario con altar), arena (arena circular).
- palette solo admite void (pizarra y violeta), nebula (purpur y magenta), eclipse (negro y ámbar).
  La paleta se aplica a todo el plano, no por sala.
- La primera sala es la entrada y debe ser gateway.
- Cada conexión: {from: id_de_sala, to: otro_id}; máximo 20; sin duplicados.
- Conectar solo celdas vecinas: abs(x1-x2)+abs(z1-z2)=1.
- Todas las salas deben ser accesibles desde la entrada. Incluir ramificaciones si encajan.
- Respetar la temática cósmica, con un ritmo entre espacios pequeños y grandes.
- Este prototipo no admite enemigos, jefes, botín, trampas, secretos interactivos,
  pisos múltiples, materiales arbitrarios ni arquitectura libre fuera de esos cinco tipos.
  Interpreta mi descripción dentro de estas posibilidades.
- El nombre no activa efectos; solo los campos anteriores cambian la construcción.

Mi descripción: [ESCRIBE AQUÍ LO QUE QUIERES]
```

No hay conexión automática entre el hosting y tu cuenta de ChatGPT.
El plugin no usa contraseñas, cookies, claves de API ni consultas de pago.
