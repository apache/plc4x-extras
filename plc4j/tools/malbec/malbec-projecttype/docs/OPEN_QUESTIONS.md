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

# Preguntas abiertas

Dudas que necesitan una decision externa antes de poder seguir. Todo lo que no
aparece aqui se puede resolver leyendo el codigo.

Estado: Fase 0 completada. B-1 y B-2 resueltas. Actualizado tras `[H-01]`.

---

## Resueltas

### B-1. T1.12 — alcance de `DisplayNodeFactory` — EXCLUIDA

**Decision.** `DisplayNodeFactory` queda fuera de la refactorizacion. No se toca en
ninguna fase.

**Alcance exacto.** La clase se mantiene intacta: sin cambios de comportamiento, sin
reformateo y sin moverla. Como tampoco se toca, sus dependencias con el resto de clases
no se modifican y no queda trabajo de desacoplamiento pendiente alrededor de ella.

**Sin pendientes.** No hace falta ninguna accion posterior para T1.12. La decision se
tomo con la Fase 0 ya cerrada, asi que la tarea nunca llego a entrar en la lista.

### B-2. H-01 — verificacion mediante costura inyectable — RESUELTA EN `[H-01]`

**Decision.** Levantar el arnes de NetBeans queda descartado, igual que la verificacion
manual. Se aisla la politica de carga del entorno.

**Como quedo.** `CommConfigLoader` (package-private) recibe el `CommConfigRepository`
por constructor y encapsulate la politica de carga. `reload()` delega en el, de modo que
la politica ya no necesita `Project`, `FileObject` ni `FileSystem`, y se puede simular
con un repositorio que falle. Despues se extrajo tambien `ProjectStorage`, con
`NetBeansProjectStorage` como unica implementacion que conoce NetBeans y una
implementacion en memoria para pruebas.

**Comprobado que los tests tienen dientes.** Se reintrodujo el `catch` de la carga y 3
tests fallaron; se neutralizo la guarda de `save()` y 3 mas fallaron. Ambas mutaciones
se revirtieron. Un test que nunca ha fallado no demuestra nada.

**Alcance real de la guarda de `save()`.** Merece una aclaracion, porque en su momento
se describio de forma imprecisa. Con un XML mal formado, `save()` sin la guarda falla
tambien, al intentar volver a parsear el archivo: lanza `IllegalStateException`. No hay
borrado silencioso en ese caso. El dano real ocurre cuando la lectura falla por un motivo
que no es de parseo —el archivo bloqueado por otra herramienta, un problema de
permisos—: ahi el archivo sigue siendo escribible y un guardado sin guarda devuelve
`true` tras sobrescribir el fichero con un modelo vacio. Ese es el escenario que
cubre `unaLecturaFallidaNoDejaSobrescribirElArchivo`, que se verifico desactivando la
guarda y observando `expected: <false> but was: <true>`.

---

## No bloqueantes

### N-1. `boot.db` no guarda `brand`, `model`, `s88Node` ni `s88Uuid`

**Pregunta.** Es intencionado que la tabla `Devices` lose esos cuatro campos que si
existen en `comunicacion.xml`?

**Como se verifico.** `SQL_CREATE_TABLE_DEVICES` (`BootDbRepository.java:35`) solo
declara `DeviceUuId`, `DriverName`, `DeviceKey`, `DeviceUrl`, `DeviceName`,
`DeviceDescription`, `DeviceEnable` y `Md5`. El XML si persiste los cuatro campos.
El golden `boot_db_reference.sql` lo deja fijo.

**Opciones.** 1. Es correcto, `boot.db` solo necesita lo de ejecucion.
2. Anadir columnas (cambia el esquema, prohibido en este plan).

### N-2. `Devices.Md5` y el resto de md5 inventados

**Resuelto.** El asistente no calcula hashes y el modelo no trae uno para el
dispositivo, asi que ya no se escribe ningun valor inventado: la columna Md5 se
deja vacia en `Devices` y los md5 que crea el asistente van vacios tambien.
Los md5 que ya vienen de un `comunicacion.xml` existente se respetan sin tocar.

Ningun consumidor en runtime lee la columna; hoy solo se muestra como texto en la
tabla de PVs. Si algun dia se decide usarla, habra que calcular el hash de verdad.
### N-3. Los booleanos se guardan como texto en SQLite

**Pregunta.** Es intencionado que `DeviceEnable`, `GroupEnable`, `ItemEnable`,
`PvScanEnable` y `PvWriteEnable` se guarden como `'true'` / `'false'` en vez de
0/1?

**Como se verifico.** Todas las columnas se declaran `TEXT` y el golden muestra los
valores entrecomillados.

**Opciones.** 1. Aceptado. 2. Migrar a INTEGER, lo que cambia el formato y obliga a
rehacer el golden de `boot.db`.

---

## Desviaciones del plan ya resueltas

Se anotan para que no pasen por omision.

### D-1. T0.2(b): el golden de `boot.db` no es un round-trip

**Lo que pedia el plan.** Un round-trip `load -> save` de `boot.db`.

**Por que no es posible.** `BootDbRepository` solo expone `createDB`,
`insertDeviceConfig` y `rebuild`. No existe ningun metodo que lea `boot.db` de
vuelta a un `CommunicationsConfig`, porque su unica responsabilidad es construir la
base a partir del modelo en memoria.

**Lo que se hizo.** Generacion determinista: se construye el modelo con datos fijos,
se llama a `rebuild()` y se compara el volcado logico resultante contra
`boot_db_reference.sql`. El volcado ordena siempre tablas, columnas y filas.

**Descartado ademas.** Comparar bytes del fichero `.db`. SQLite no garantiza
reproducibilidad binaria, asi que daria falsos negativos ante refactors inocuos.

### D-2. El golden usa datos sinteticos, no un `comunicacion.xml` real

**Por que.** El proyecto `llenadora` contiene datos corruptos: un area sin rango
(`%DB1[]`) y dos areas solapadas sobre `%DB21.DDB4` (`INTEGER` en `[0..9]` frente a
`REAL` en `[0..12]`). Alimentar el golden con eso fijaria la corrupcion como
comportamiento esperado y bloquearia cualquier correccion futura.

**Pendiente de decision del usuario.** Si esos datos se deben reparar, y con que
criterio. Ver N-4 de la seccion siguiente.

### D-3. T1.12, T2.5, T6.2 y T6.5 no se ejecutan

| Tarea | Motivo |
|---|---|
| T1.12 | `DisplayNodeFactory` excluida de la refactorizacion por decision (ver B-1). |
| T2.5 | `commsHmi` ya es `nbm` con el jar embebido via `X-Class-Path`, sin dependencias de NetBeans, y un spike previo verifico que funciona. |
| T6.2 | `HMIPanelImportAction` ya usa `sealed interface`, `record`, `ProgressUtils`, filtro de extension y `Logger`. |
| T6.5 | El aviso de formatos desalineados ya se emite al abrir el importador, no al pulsar «Importar». |

---

## Comprobaciones de integridad H-02 a H-09

Los ocho hallazgos del plan se han contrastado con el codigo. **Los ocho se
confirman**, pero no todos con la misma severidad ni con el mismo alcance. Donde ha
sido posible se ha fijado el comportamiento actual con tests de caracterizacion.

**Los tests de caracterizacion describen el fallo, no la especificacion.** Cuando se
corrija el hallazgo, el fallo debe observarse ahi: o el test pasa con el
comportamiento nuevo, o se reescribe como test del comportamiento correcto. Ajustar
la asercion para que cuadre con el fallo seria perder lo que protege.

| # | Confirmado | Sev | Fijado con test | Tarea del plan |
|---|---|---|---|---|
| H-02 | Si | Alta | Si | T1.1 |
| H-03 | Si | Alta | Si | T1.5 |
| H-04 | Si, con matiz | Media | Si | T1.5 |
| H-05 | Si | Alta | Corregido, `[T1.3]` | T1.3 |
| H-06 | Si | Media | No | T1.4 |
| H-07 | Si | Media | Corregido, `[T1.4]` | T1.4 |
| H-08 | Si, los 5 sub-casos | Alta | (a) y (b) corregidos, `[T1.6]` | T1.6 |
| H-09 | Si | Alta | No | T1.7 |

### H-02 — la escritura trunca el archivo antes de serializar — ALTA

`PlcOpenCommConfigRepositoryImpl.save()` abre el destino con `storage.create()` y
solo despues serializa. `ProjectFileStorage.create()` llama a
`fileObject.getOutputStream()`, que vacia el archivo en ese instante, y sin
`FileLock`. Un fallo a mitad deja el archivo vacio o truncado y el contenido anterior
perdido.

Fijado en `PlcOpenSaveIntegrityTest` con un storage que reproduce el orden real de
operaciones: el contenido anterior desaparece en cuanto se pide el destino, y tras
un fallo de escritura no sobrevive ni un resto.

### H-03 — `boot.db` sin transaccion global — ALTA

`rebuild()` hace `DROP TABLE` de las cuatro tablas y cierra la conexion; despues
`createDB()` las recrea en otra conexion; y a continuacion inserta dispositivo por
dispositivo, cada uno con su propia conexion. El `DROP TABLE` ya esta confirmado
cuando empieza el primer `INSERT`, asi que un fallo intermedio destruye la base
anterior valida y deja un estado parcial.

Fijado en `BootDbRepositoryIntegrityTest`:

- Un fallo en el segundo dispositivo deja el primero escrito y el segundo no, y
  `rebuild()` devuelve `false`.
- Una base buena seguida de un guardado invalido deja la base **anterior
  destruida y vacia**, no intacta. Este es el caso grave: `comunicacion.xml` ya se
  habia guardado antes de llamar a `rebuild`.

### H-04 — valores inventados — MEDIA, con un matiz

`insertDeviceConfig()` rellena con literales cuando no hay dispositivo: `DefaultDriver`
como driver, `tcp://localhost:502` como URL, `Key123` como clave y la descripcion
fija `Dispositivo guardado desde DeviceManager`. Ademas, con dispositivo pero sin
`deviceKey`, tambien escribe `Key123`.

**El plan se equivoca en un punto:** afirma que los `Md5` de grupos, areas y PVs
tambien son literales. No lo son. Solo el del dispositivo lo es
(`"md5_device_hash"`); los otros salen del modelo (`g.getMd5()`, `item.getMd5()`,
`pv.getMd5()`). Fijado en `BootDbRepositoryIntegrityTest`.

Tambien hay un matiz a favor: una clave **vacia** si se respeta, solo una clave
**nula** se sustituye por `Key123`.

### H-05 — NPE en clic derecho y recorrido recursivo — ALTA

`findExistingCategoryFile()` devuelve `null` si no encuentra ningun fichero marcador
(linea 225) y `getActions()` hace `switch` sobre ese `String` (linea 175). En Java un
`switch` sobre `String` nulo lanza `NullPointerException`, y la rama `default` no lo
evita. Se dispara al hacer clic derecho sobre un proyecto sin marcador.

Ademas `hasChildFile()` recursiona sobre todo el arbol, y se llama una vez por cada
uno de los diez ficheros marcador: diez recorridos completos del proyecto por cada
clic derecho.

No testeable sin `FileSystem`. **Corregido en `[T1.3]`**: la deteccion se extrajo a
`DeteccionCategoria`, sobre una vista `ArbolProyecto` que el test construye en memoria.

- `DeteccionCategoria.detectar()` devuelve `Optional`, nunca `null`, asi que el
  `switch` ya no puede recibir un nulo. Un proyecto sin marcador devuelve acciones
  vacias en vez de lanzar `NullPointerException`.
- **De diez recorridos a uno.** Antes se llamaba a una busqueda recursiva por cada
  fichero marcador, y cada una recorria el arbol entero. Ahora se recogen todos los
  nombres en una sola pasada con una pila explicita, en lugar de recursividad, para
  que un arbol profundo no desborde la pila.
- Se elimino tambien una comprobacion redundante: `child.getFileObject(fileName)` ya
  estaba cubierta por la recursion.
- **Detalle que importa:** solo cuentan los ficheros, no las carpetas. Una carpeta
  llamada `comunicacion.xml` no es el fichero marcador. Un test detecto esto porque
  la primera version de la deteccion aceptaba carpetas; el codigo original no lo hacia.

Cubierto por `DeteccionCategoriaTest` (11 tests).

### H-06 — `attachMessage` no registra — MEDIA

`HMIPanelCategorySubprojectImpl.getLookup()` envuelve la creacion del modelo en
`catch (Throwable)` y solo llama a `Exceptions.attachMessage`, que anota la excepcion
pero no la registra. Si `HMICommunicationModel` falla al inicializarse, el modelo se
omite del Lookup y el usuario no ve ningun indicio.

Relevante por H-01: era justo el mecanismo que hacia invisible el fallo anterior. La
interfaz usa el modelo y el guardado silencioso ya no es posible, pero el aviso al
usuario sigue sin implementarse.

No testeable sin `Project`. Es el arreglo de T1.4.

### H-07 — catch vacio en `loadProjects()` — MEDIA

`HMIPanelCategorySubprojectProviderImpl.loadProjects()` (en `HMIPanelSubproject`,
linea 73) tiene `catch (Exception e) { }` completamente vacio: un subproyecto que
falle al cargarse desaparece del arbol sin aviso.

Es **el unico `catch` vacio que queda en los cuatro modulos**, asi que T1.4 esta a un
cambio de cumplir su criterio de aceptacion. El mismo metodo tiene ademas un bug
latente: recibe `dir` como parametro y lo ignora, usando `project.getProjectDirectory()`.

### H-08 — importacion de planta, los cinco sub-casos — ALTA

| | Sub-caso | Estado |
|---|---|---|
| a | `image` ausente y retorno silencioso | **Confirmado y era el mas grave**: `reversechildstack` hacia `return` si `baseDir` era `null` (linea 511), y el flujo seguia hasta `return new Exito(...)` (linea 237). Se anuncia «importacion exitosa» sin haber creado ninguna carpeta. **Corregido en `[T1.6]`** |
| b | excepcion por nodo y se continua | Confirmado (linea 548): se imprimia la excepcion y el bucle seguia; el resultado se reportaba como exito sin agregar los fallos. **Corregido en `[T1.6]`**: `reversechildstack` devuelve cuantos nodos fallaron y un fallo parcial ya no se anuncia como exito |
| c | carpetas antes que `plant-model.xml` | Confirmado (lineas 218-231): si el volcado falla se informa del fallo, pero las carpetas ya creadas quedan sin rollback |
| d | `node.getId()` sin sanear | **Corregido en `[T1.6]`**: ver abajo |
| e | `DocumentBuilderFactory` sin endurecer | **Corregido en `[T1.6]`**: ver abajo |

No testeable sin `FileSystem`. Requiere el arreglo de T1.6.

### H-08(d) y H-08(e), corregidos en `[T1.6]`

**H-08(d), path traversal.** El `id` viene del fichero que elige el usuario y se usaba
tal cual como nombre de carpeta y de fichero. Ahora `problemaDeNombre()` rechaza
`/ \ : * ? " < > |`, cualquier `..`, y nulos, vacios o caracteres de control en los
extremos.

- Se **rechaza** en lugar de renombrar: renombrar en silencio crearia carpetas con un
  nombre que el usuario no reconoce.
- `primerNombreInvalido()` recorre todo el modelo **antes** de crear la primera
  carpeta, para que un id invalido no deje estructura a medias.
- `..` se comprueba antes que los caracteres invalidos, porque un traversal merece el
  aviso mas grave.
- Queda una guarda defensiva en `reversechildstack`, que es publico.

**H-08(e), XXE.** Nueva clase publica `XmlSafe` en el paquete `panelcategory.xml`, con
el criterio compartido: la usan el importador y `PlantModelReader`, que ya se endurecia
pero por su cuenta y podia divergir.

**La vulnerabilidad era real y queda demostrada.** Una sonda con una
`DocumentBuilderFactory` sin endurecer en JDK 21 resolvio una entidad externa y filtro
al DOM 85 caracteres de `C:/Windows/win.ini`. Verificado empiricamente, no supuesto.

**Dos detalles del proceso que conviene no pasar por alto:**

1. El plugin nbm detecto que `panelcategory.xml` no era accesible en runtime y fallo el
   build. Sin esa comprobacion habria sido un `NoClassDefFoundError` al desplegar. El
   paquete quedo exportado como `publicPackage`; la dependencia entre los dos modulos ya
   existia, asi que no se creo ninguna arista nueva.

2. Mi primer payload de test era **invalido** y por eso los tests de XXE pasaban sin
   probar nada. XML prohibe referencias a entidades externas dentro de valores de
   atributo, de modo que `id="&robado;"` lanzaba siempre, con o sin proteccion. La
   mutacion lo destapo. El payload correcto usa contenido de elemento
   (`<area>&robado;</area>`), y con el los tests fallan sin proteccion.

### Capas de proteccion y por que estan todas

Con `disallow-doctype-decl` no se admite ninguna `DOCTYPE`, y con ella llegan las
entidades: es la proteccion que manda. Las demas (entidades externas, DTD externo,
XInclude) son defensa en profundidad y se ignoran en silencio si el parser no las
soporta, porque con la primera activa no hay nada que las alcance. Si la principal no se
puede activar, se propaga la excepcion: es preferible no parsear antes que parsear sin
proteccion.

### H-09 — Phoebus: memento global y pipe sin drenar — ALTA

`HMICategoryOpenPhoebusAction`:

- **Sobrescribe `~/.phoebus/memento`** (linea 110) sin copia de seguridad. Es estado
  global de sesion del usuario, y se pisa cada vez que se abre un `.bob`.
- **Pipe sin drenar**: `redirectErrorStream(true)` (linea 118) fusiona stderr en
  stdout, `pb.start()` (linea 119) descarta el `Process` y nadie lee la salida. Si el
  proceso escribe mas que el buffer del pipe, **se queda bloqueado**.
- **Pasa `"-edit", ""`** (lineas 114-115) con cadena vacia, que probablemente no es
  lo que Phoebus espera.

Esto confirma que **N-5 sigue bloqueando** la parte de T1.7 que consiste en pasar el
`.bob` por linea de comandos: sin saber la CLI real no se puede saber si la alternativa
al memento existe.

---

## T2.2 — limpieza de dependencias: PAUSADA

**Decision.** La tarea queda en pausa en esta etapa. No se elimina ninguna
dependencia.

**Motivo aprobado.** Tocar el `malbec/pom.xml` raiz para actualizar
`maven-dependency-plugin` queda fuera del alcance actual. Y adivinar que
dependencias del runtime de NetBeans sobran, sin un test de integracion de arranque,
introduce un riesgo inaceptable de romper la carga por `Lookup` o por `layer.xml`.

El resto de este apartado es la evidencia que sustenta la decision, por si alguien
quiere retomarla mas adelante.

### Por que falla la herramienta

`mvn dependency:analyze` falla con `Unsupported class file major version 65`. Es la
version de bytecode de Java 21, que la version 3.4.0 de `maven-dependency-plugin` no
sabe leer. Actualizar el plugin exigiría tocar `malbec/pom.xml`, fuera del alcance de
este plan.

### Por que el analisis manual tampoco es concluyente

Se hizo a mano: se leyeron los paquetes reales de cada jar `org.netbeans.api` y se
buscaron imports coincidentes. El resultado es orientativo, no una conclusion:

| Modulo | NB declaradas | Con import directo | Sin import directo |
|---|---:|---:|---:|
| `ConfigPhoebus` | 25 | 5 | 20 |
| `HMIPanelSubprojectCategory` | 30 | 17 | 13 |
| `HMIPanelSubproject` | 30 | 16 | 14 |
| `commsHmi` | 0 | 0 | 0 |

`commsHmi` ya cumple su objetivo de 0 dependencias NB. Los otros tres no.

**"Sin import directo" no es "sin uso".** Hay tres mecanismos de uso que ningun
analisis de imports ve:

1. **Rutas de layer.** `NodeFactorySupport.createCompositeChildren(project,
   "Projects/org-apache-plc4x-panel/Nodes")` y
   `Utilities.actionsForPath("Projects/org-plc4x-project/Actions")` resuelven clases
   en runtime a partir de una ruta de string. No hay ningun import que las delate.
2. **Lookup.** `Lookup.getDefault().lookup(...)` y los `@ServiceProvider` resuelven
   implementaciones en runtime.
3. **Requisito de modulo en runtime.** El plugin nbm convierte las dependencias Maven
   en `OpenIDE-Module-Dependencies`. En un modulo de NetBeans, la lista de
   dependencias *es* parte del cableado que carga el sistema de modulos.

El caso de `ConfigPhoebus` lo ilustra: **2 ficheros Java y 25 dependencias NB**. Sus
dependencias son su cableado, no imports. Bajarlo a las 8 que sugiere el plan seria
adivinar.

### La red de seguridad del plan no esta disponible

El plan dice "verificando `nbm:manifest` y arranque". El manifest se puede comprobar, y
de hecho asi se verifico que quitar `ConfigPhoebus` de `HMIPanelSubproject` se
propagaba correctamente al NBM. El **arranque no se puede comprobar**: hace falta el
runtime de NetBeans, y en este build no hay ningun `FileSystem` registrado. Ya se
vio con H-01: sin la plataforma, ninguna de estas dependencias se puede validar en
ejecucion.

Precedente relevante: en `[T1.6]` el plugin nbm detecto un paquete no exportado que
ningun analisis estatico habria visto. Un fallo de runtime aqui seria igual de
invisible.

### Que hace falta para cerrar T2.2

1. Actualizar `maven-dependency-plugin` a una version que soporte Java 21, en
   `malbec/pom.xml`.
2. O una verificacion de arranque real: instalar la aplicacion y abrir un proyecto,
   recorriendo el arbol, el wizard y la importacion.

Mientras no haya una de las dos, la limpieza de dependencias no es verificable y queda
documentada en lugar de aplicada.

---

## Pendiente de informacion externa

### N-4. Datos corruptos en el proyecto `llenadora`
**Situacion.** `Comunicacion/comunicacion.xml` tiene un area sin rango y dos areas
solapadas. No se ha tocado el fichero.

**Pregunta.** Hay que repararlos, y quien decide el criterio de solapamiento?

### N-5. CLI de Phoebus (T1.7)

**Pregunta.** Cual es el ejecutable y la linea de comandos real para generar un
proyecto Phoebus desde linea de ordenes?

**Por que importa.** `ConfigPhoebus` no tiene ningun test (0 tests en el baseline)
y todo lo que se declare sobre su CLI quedaria sin verificar hasta saberlo.

### N-6. Cambio local ajeno, fuera de alcance
`plc4j/tools/malbec/application/pom.xml` tiene un cambio sin commitear que sustituye
`org-apache-commons-lang3` de `RELEASE130` a `${netbeans.version}`. Ese modulo esta
fuera del alcance de este plan, asi que se ha dejado intacto en el arbol de trabajo
y sin commitear en todas las fases.

---

## Notas de medicion

El plan estimaba ~260 tests en los cuatro modulos y ~180 en `commsHmi`. Los valores
reales del baseline fueron 247 y 166. Las estimaciones no sirven como medida de
exito; `docs/BASELINE.md` tiene el detalle por modulo.
