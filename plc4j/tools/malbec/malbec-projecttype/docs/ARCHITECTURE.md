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
# Arquitectura del panel de Malbec

Este documento responde a cuatro preguntas que salen una y otra vez al tocar este código:
qué módulos hay, qué puede depender de qué, y qué son exactamente los tres ficheros que se
escriben dentro de cada proyecto.

Lo que hay aquí está comprobado contra el código. Cuando algo cambió y este documento no,
es un fallo del documento, y conviene arreglarlo en el mismo commit.

---

## 1. Módulos y dependencias permitidas

`plc4j/tools/malbec/malbec-projecttype` es un agregador: cada carpeta es un módulo Maven y
tiene su propio `pom.xml`. Los que forman el núcleo del panel de operador son cuatro, y el
resto (`comms`, `events`, `htc`, `pics`, `recipes`, `scripts`, `securities`, `tgl`, `udt`,
`information`, `hmi`) son anteriores y se mantienen aparte.

Las dependencias **entre los cuatro del núcleo** son estas, y solo estas:

```
commsHmi                  (no depende de nada del núcleo; es la base)
   ^
   |
HMIPanelSubprojectCategory
   ^
   |
HMIPanelSubproject
```

| Módulo | Qué contiene | Depende de |
|---|---|---|
| `commsHmi` | El modelo de datos (`CommConfigData`, `DeviceConfigData`, `CommConfigData.PvConfig`), los repositorios de XML y SQLite, los constructores de panel por protocolo, `SaveResult`, `PanelBuilderRegistry`, `TagDiagnostico` | — |
| `HMIPanelSubprojectCategory` | Los nodos del árbol de categorías, el modelo `HMICommunicationModel` y sus guardas de concurrencia, los diálogos (asistente de comunicación, alta de dispositivo) | `commsHmi` |
| `HMIPanelSubproject` | El nodo raíz del panel, la exportación a ZIP y, en el paquete `...panel.plant`, la importación desde S88 | `HMIPanelSubprojectCategory` |
| `ConfigPhoebus` | Ajuste de la ruta del ejecutable de Phoebe | — |

### Reglas

1. **La dependencia va en una sola dirección.** `commsHmi` no sabe que existen los paneles. Si
   aparece una importación de `HMIPanelSubprojectCategory` dentro de `commsHmi`, algo se ha
   mezclado.
2. **`commsHmi` no depende de Swing para su lógica.** El modelo, los repositorios y los
   builders se prueban sin pantalla. Cuando hay que mostrar algo, quien lo hace es el panel.
3. **Un panel no se instancia desde otro módulo de la aplicación** sin pasar por su
   `Lookup` (ver más abajo el registro de `HMICommunicationModel`).

### Fuera del núcleo

`hmi`, `comms`, `events`, `htc`, `pics`, `recipes`, `scripts`, `securities`, `tgl`, `udt` e
`information` son módulos anteriores del plugin. **No forman parte del gate de calidad**: el
comando de verificación de abajo no los compila, y no se les exige cobertura de pruebas. Se
toca lo mínimo imprescindible y se anota.

---

## 2. Cómo se registra el modelo de comunicaciones

`HMIPanelCategorySubprojectImpl.getLookup()` construye el `Lookup` del proyecto y registra
`HMICommunicationModel` en él. Todo lo demás lo obtiene con:

```java
project.getLookup().lookup(HMICommunicationModel.class)
```

Hay un `catch (Throwable)` alrededor de esa construcción que **omite el modelo si falla**, para
que un modelo roto no tumbue el árbol de proyectos. Desde `5cb395c4` el fallo **se registra en
el log** con nivel `SEVERE` y el nombre del proyecto.

> Consecuencia práctica: si el modelo no está en el `Lookup`, el asistente de comunicación se
> abre **vacío**, sin dispositivos. Es un síntoma claro, no un fallo silencioso.

---

## 3. `comunicacion.xml`

Dónde vive: en la carpeta `Comunicacion/` del proyecto, junto a `comm.merlot`, `category.cfg`
y `boot.db`. La ruta se define en `ProjectIds.COMMUNICATION_CONFIG_FILE`.

Formato: **no es XML propio**, es un proyecto PLCopen (`.xml` de la familia
`tc6_0201`) con los datos de Malbec dentro de `<instances><configurations>`:

```xml
<project xmlns="http://www.plcopen.org/xml/tc6_0201">
  <instances>
    <configurations>
      <configuration name="PLC_Control">
        <addData>
          <data name="http://apache.org/plc4x/malbec/device" handleUnknown="preserve">
            <device uuid="..." enabled="true" s88Uuid="..." xmlns="Device">
              <brand>Siemens</brand>
              <model>S7-1500</model>
              ...
            </device>
          </data>
        </addData>
      </configuration>
    </configurations>
  </instances>
</project>
```

Cada variable de proceso (`PvConfig`) tiene **19 campos**: uuid, nombre, tipo, área, offset,
descriptor, scanTime, scanEnable, writeEnable, límite de display bajo, límite de display alto,
descripción, formato, unidades, límite de control bajo, límite de control alto, minStep, md5 y
ruta S88.

La tabla del panel muestra **veinte columnas**, una más que los campos del record: el nombre
del área se resuelve al pintarla. `boot.db` sí guarda los 19.

### Regla H-01, la más importante de este fichero

> Si la carga falla, **no se escribe nada**.

`HMICommunicationModel.reload()` captura el fallo, conserva la configuración anterior y marca
`loadError`. Mientras `loadError` esté puesto, `save()` **se niega a escribir** y devuelve un
`SaveResult` con `xmlSaved() == false`.

El motivo: un `comunicacion.xml` ilegible dejaría el modelo en memoria vacío, y un guardado
posterior volcaría ese vacío encima del fichero, **destruyendo datos que sí estaban ahí**.
Por eso `load()` está dentro de un `try`, y por eso los tests
`unXmlDanadoImpideGuardarYNoTocaBootDb` y `unaLecturaFallidaNoDejaSobrescribirElArchivo`
existen: verifican que quitar la guarda los hace fallar.

---

## 4. `boot.db`

Dónde vive: `Comunicacion/boot.db`. Es una base **SQLite**, y no la fuente de verdad: se
**reconstruye entera** desde `comunicacion.xml` cada vez que se guarda
(`BootDbRepository.rebuild()`, que delega en `BootDbWriter`). Cuatro tablas:

| Tabla | Clave | Contenido |
|---|---|---|
| `Devices` | `DeviceUuId` | marca, modelo, url, nombre, descripción, enable, md5 |
| `Groups` | `GroupUuid` | dispositivo, nombre, descripción, scantime, enable, md5 |
| `Items` | `ItemUuid` | dispositivo, grupo, nombre, tag, enable, md5 |
| `PvRecords` | `PvUuId` | nombre, tipo, área, offset, descriptor, scanTime, enables, límites, formatos, md5, ruta S88 |

La reconstrucción **no toca la base de destino hasta el final**. `BootDbWriter` construye
el esquema y mete todos los dispositivos en `boot.db.tmp`, con una sola conexión y una sola
transacción, y solo entonces mueve el resultado encima con `ATOMIC_MOVE`. O sale la base
nueva completa, o sigue la anterior intacta: no hay estado intermedio en el que
`comunicacion.xml` y `boot.db` no concuerden.

El `PRAGMA user_version` del fichero lleva la versión del esquema (`BootDbWriter.SCHEMA_VERSION`).
El `PRAGMA foreign_keys` se pide antes de abrir la transacción, porque SQLite lo ignora
dentro de ella.

### Lo que no se inventa en `boot.db`

Todo lo que se escribe sale del modelo o se deja vacío. En concreto:

- **Ningún `Md5` es inventado.** `Devices` no tiene hash en el modelo, así que su `Md5`
  va vacío, y el asistente deja vacíos también los de grupo, área y PV. Los md5 que ya
  vengan de un `comunicacion.xml` existente se respetan. Nadie lee esta columna en
  runtime; se decidió dejarla vacía hasta que se sepa si se va a usar.
- **La clave del dispositivo es obligatoria.** Si `deviceKey` falta o está en blanco, el
  dispositivo no se escribe y el guardado falla nombrando el cuál. Antes se rellenaba con
  una clave fija que el runtime recibía de verdad.
- **No existe la vía sin dispositivo.** El overload `insertDeviceConfig(ruta, comms)` se
  eliminó porque rellenaba driver, url, clave y descripción con literales, y no lo
  llamaba nadie de producción.

Cuando el guardado falla, `HMICommunicationModel` mete el motivo en `SaveResult.parcial()`,
así que el diálogo dice qué dispositivo hay que arreglar en vez de un "no se pudo
actualizar `boot.db`" sin más.

Los valores booleanos se guardan como texto (`"TRUE"` / `"FALSE"`), igual que se muestran en
las tablas del panel. Es una rareza histórica del formato, no un error.

---

## 5. `plant-model.xml`

Dónde vive: en la raíz del proyecto, o **en una carpeta por encima** (ver más abajo). Es el
modelo de planta que exporta el S88 y del que salen las variables disponibles.

```xml
<project>
  <area id="TANQUE_1" uuid="uuid-t1"/>
  <area id="AGITACION_TANQUE_1" uuid="uuid-t2"/>
  ...
</project>
```

Solo se leen los elementos `<area>`, y de cada uno únicamente `id` y `uuid`. El resto del
árbol (`<processcell>`, `<element>`, ...) se escribe pero **no lo lee nadie**: los tres
lectores -`PlantModelReader`, `PlantNodeLoader` y el borrado de áreas del importador- buscan
exclusivamente `<area>`.

### El nombre de la carpeta se busca subiendo

`PlantNodeLoader` sube desde la carpeta del proyecto hacia la raíz buscando `plant-model.xml`,
y gana el más cercano. **Esto es intencionado y no se debe cambiar sin avisar**: hay proyectos
con el modelo de planta un nivel por encima, en la carpeta que agrupa las máquinas.

### Niveles del S88 y sus nombres XML

`PlantSnapshot.tagFor()` decide el nombre del elemento. Solo hay dos nombres propios:

| Nivel de `S88Level` | Elemento escrito |
|---|---|
| `AREA` | `<area>` |
| `PROCESSCELL` | `<processcell>` |
| `UNIT`, `EQUIPMENTMODULE`, `NULL`, sin nivel | `<element>` |

Que `UNIT` y `EQUIPMENTMODULE` caigan en `<element>` es **deliberado**: como ningún lector
distingue esos nombres, darles uno propio solo cambiaría el aspecto del fichero generado.
Documentado en el propio método.

---

## 6. Verificación

El gate de los cuatro módulos del núcleo, con y sin pantalla:

```bash
mvn -o -B -pl commsHmi,ConfigPhoebus,HMIPanelSubprojectCategory,HMIPanelSubproject -am clean verify
mvn -o -B -pl commsHmi,ConfigPhoebus,HMIPanelSubprojectCategory,HMIPanelSubproject -am clean verify -DargLine=-Djava.awt.headless=true
```

Son **483 pruebas**. El `clean` no es opcional: sin él Maven puede dar un módulo por
actualizado y ejecutar clases viejas, y el resultado habla de un código que ya no existe.

Los Golden Files de `commsHmi/src/test/resources/golden/` fijan el formato exacto de
`comunicacion.xml` y de `boot.db`. **No se editan a mano**: si cambian, o cambia el formato a
propósito y se regeneran explicándolo, o hay un bug.

### Guardar no puede perder lo anterior

Tanto `comunicacion.xml` como `boot.db` se escriben de forma que un fallo a mitad no
destruye lo que habia:

- `comunicacion.xml`: `save()` serializa entero en memoria y entrega el resultado al
  almacenamiento con un solo `CommConfigStorage.write(byte[])`. Antes pedia el destino con
  `create()`, que vacia el fichero al abrirlo, de modo que un fallo durante la serialización
  dejaba el fichero vacío y perdía todos los dispositivos. `ProjectFileStorage` toma además
  un `FileLock` antes de escribir, para que dos guardados simultáneos no se pisen.
- `boot.db`: se construye en `boot.db.tmp` y se mueve al final. Ver la sección 4.

Ambos comportamientos están fijados por tests que fallan contra el código anterior.

### Lo que los tests NO cubren

- **La apariencia de los diálogos.** No hay forma de comprobar con un test que un panel se ve
  bien; eso está en `docs/MANUAL_VALIDATION.md` y necesita la aplicación.
- **Los manejadores de botón** (`agregarPv`, `addGroup`, `addItem`...) abren `JOptionPane`, así
  que no corren en headless.
- **El hilo del aviso de cambios.** `cs.fireChange()` se llama desde el hilo del watchdog de
  ficheros, no desde el de Swing. Es una deuda conocida: los listeners actualizan nodos del
  árbol desde un hilo ajeno al de la interfaz. Está anotado en `HMICommunicationModel` y
  pendiente de decidir.