<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->
# Validacion manual pendiente en el IDE

Estos puntos no los cubre el build: dependen del runtime de NetBeans. Estan
pendientes de comprobacion manual antes de dar por cerrada la Fase 4.

## T4.2 - consolidation de las factories de categoria

Refactor aplicado: ocho NodeFactory de categoria, cada una con su copia de
unos 195 lineas, ahora extienden CategoryFilesNodeFactory y ocupan 34-40
lineas cada una. Lo que decide que se muestra ya no vive en ellas, sino en
HMICategoryDefinition (T4.1), que esta probado.

El gate comprueba que cada clase apunta a su categoria, conserva su posicion en
el arbol y que la que anade accion propia sigue anadiendola. Lo que **no**
comprueba es lo siguiente:

- [ ] El arbol del proyecto se ve igual que antes: mismos nodos, mismo orden.
- [ ] Los nodos se refrescan al crear, renombrar y borrar un fichero dentro de
      la carpeta de una categoria.
- [ ] El fichero base (ase.rcp, ase.wmg, ...) sigue sin aparecer.
- [ ] Las carpetas que no son de una categoria siguen mostrando una lista vacia.
- [ ] Editar un dispositivo y cambiar de modelo sigue limpiando la conexion.
- [ ] Las acciones propias (Graficos, Usuarios runtime, Configuracion de panel)
      siguen apareciendo en el menu contextual de sus ficheros.

## Por que no se automatiza

@NodeFactory.Registration la procesa NetBeans en tiempo de compilacion y no
queda en el class con retencion RUNTIME, asi que no se puede leer por
reflexion desde un test. Y el comportamiento del arbol solo se observa con el
IDE abierto. Las posiciones se pueden comprobar porque viven en
CategoryNodePositions, que si es codigo normal: ahi esta la unica copia y la
anotacion la referencia.

## T4.3 - division de la factory de Comunicacion

407 lineas partida en cinco ficheros: `HMICategoryCommunicationNodeFactory` (53),
`CommunicationNodeList` (145), `DeviceNode` (219), `DeleteDeviceDialog` (103) y
`DeviceNodeLabels` (122). Los textos que ve el usuario se movieron sin cambios: se
comparo automaticamente el conjunto de cadenas del fichero original contra los nuevos,
resolviendo escapes Unicode. 46 antes, 46 despues, ninguna diferencia.

Lo que **no** cubre ningun test, porque necesita `JOptionPane`:

- [ ] Confirmar que al cancelar el borrado no ocurre nada.
- [ ] Confirmar que el aviso de "guardado parcial" sale cuando se borra del XML pero
      no de `boot.db`. Este aviso se puede quitar por error y el build no lo nota:
      es el hueco de cobertura mas relevante de este refactor.
- [ ] Confirmar que el error de guardado (excepcion) se avisa y no se traga.
- [ ] Confirmar que la hoja de Propiedades muestra los once campos.
- [ ] Confirmar que "Modificar" avisa cuando el dispositivo ya no esta.
- [ ] Confirmar que el arbol repinta la etiqueta al renombrar un dispositivo.

## T4.5 - limpieza de salidas por consola (K-07)

Las cuatro acciones de categoria pendientes de implementar ya no imprimen en consola:
muestran un aviso. La consola del IDE queda limpia durante el uso normal.

- [ ] Al pulsar "Editar panel de operador" sale el aviso y no aparece nada en la consola.
- [ ] Lo mismo para "Editar textos y graficos", "Editar gestion de avisos" y
      "Editar usuarios runtime".
- [ ] El aviso nombra la accion concreta, para saber cual falta.

### Fuera del alcance de esta tarea

El modulo `hmi/` (un plugin aparte, `Plc4xHMISubProjectProviderImpl` y
`Plc4xHMISubProjectNodeFactoryImpl`) tiene tres `System.out` de traza. No se han tocado
porque ese modulo no esta en el gate de cuatro modulos que se ejecuta aqui: cualquier
cambioallo quedaria sin comprobar. Anotado para cuando se incluya.

Tambien queda codigo comentado muerto con `System.out` dentro; no se ejecuta, y el test
de regresion lo admite a proposito, porque lo que K-07 prohibe son llamadas vivas.

## T4.6 (paso 1) - resultado de guardado tipado

`save()` ya no devuelve un `boolean`: devuelve un `SaveResult` que dice por separado si se
escribio `comunicacion.xml` y si se reconstruyo `boot.db`. Eso cambia lo que ve el usuario
en dos sitios, y es lo que hay que mirar:

- [ ] Al guardar un dispositivo con la configuracion rota, el aviso dice "No se pudo
      guardar el dispositivo" y NO dice que se guardo en comunicacion.xml. Antes lo decia
      aunque no se hubiera escrito nada.
- [ ] Si el XML se escribe pero `boot.db` no, sigue apareciendo el aviso de "Guardado
      parcial" con el texto de siempre.
- [ ] Al borrar un dispositivo, los tres avisos (borrado correcto, guardado parcial y
      error al eliminar) siguen apareciendo en los mismos casos que antes.
- [ ] Si el guardado falla al escribir el fichero, el error se ve en un aviso y no solo en
      la consola. Antes salia como excepcion.

### Lo que este paso NO cambia

No se ha tocado ni un hilo: `RequestProcessor`, el `ReadWriteLock` sobre la configuracion
y mover `fireChange` al EDT siguen pendientes del paso 2. Aqui `save()` sigue siendo
sincrono y `cs.fireChange()` se sigue llamando desde el hilo que guarda, igual que antes.
## T4.6 (paso 2) - bloqueo de la configuracion

`HMICommunicationModel` ya no comparte `config`, `loadError` y `repository` sin
proteccion: el guardado, la recarga y el borrado toman el bloqueo de escritura, y
los accesores el de lectura.

### Lo que si cubren los tests

- Que una recarga no se cuela en un guardado a medias.
- Que una lectura espera a que el guardado termine.
- Que un listener que vuelve a leer el modelo no se cuelga en el mismo hilo.

### Lo que NO cubren los tests (mirar a mano)

- [ ] Guardar un dispositivo mientras se toca `comunicacion.xml` desde fuera: la
      ventana de NetBeans no debe quedar congelada ni parpadear con datos viejos.
- [ ] Con dos o mas proyectos de comunicacion abiertos, guardar en uno no debe
      bloquearel otro.
- [ ] El aviso de cambio de configuracion sigue apareciendo al guardar, y la
      lista de dispositivos se refresca sola.
- [ ] Con la lista de dispositivos abierta y una recarga viniendo del watcher, los
      nodos no deben desaparecer de forma intermitente.

### Lo que este paso NO cambia

`fireChange()` se sigue lanzando desde el hilo que guarda, no en el EDT: es lo que
hacia antes y cambiarlo ahora no se podria comprobar sin el runtime de NetBeans.
`RequestProcessor` tampoco se ha tocado.
## T5.6 - guardar y guardar y cerrar en el asistente

Los dos botones hacen ahora el mismo proceso, con distinto final. El aviso depende de
lo que paso de verdad, y hay tres casos:

- [ ] Guardado completo: "Guardado en comunicacion.xml y en boot.db".
- [ ] Guardado parcial (XML si, base no): "Guardado parcial", con el aviso de revisar
      la salida de la aplicacion.
- [ ] No se guardo nada (por ejemplo, la carga de comunicacion.xml fallo): ahora sale
      "No se guardo nada: ..." con el motivo, y el dialogo **no se cierra**.

- [ ] Con cambios sin guardar, pulsar Cancelar y elegir "Si, guardar": si el guardado
      queda a medias, el dialogo deberia seguir abierto, no cerrarse en silencio.
- [ ] Con cambios sin guardar, pulsar Cancelar y elegir "No": cierra sin guardar.
- [ ] Pulsar "Guardar" (sin cerrar) deja el dialogo abierto con la lista al dia.