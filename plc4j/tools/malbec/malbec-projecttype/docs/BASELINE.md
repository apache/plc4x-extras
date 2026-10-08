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

# Baseline de tests — Malbec projecttype

Registro del estado inicial del refactor por fases, tomado **antes** de cualquier
cambio del plan. Sirve para detectar regresiones al comparar tras cada fase.

## Origen del baseline

| | |
|---|---|
| Fecha | 2026-10-08 14:46 |
| Rama | `refactor/hmi-modules` |
| Commit base | `1d7ba59a` — *Converting the commsHmi module from a JAR to a NetBeans module* |
| Rama de partida | `feature/app` |

## Comando de verificación

Se ejecuta desde `plc4j/tools/malbec/malbec-projecttype`:

```bash
mvn -o -B -pl commsHmi,ConfigPhoebus,HMIPanelSubprojectCategory,HMIPanelSubproject -am verify
```

Resultado: **BUILD SUCCESS**, 42 goals, 47,7 s.

## Recuento real por módulo

Extraído de los informes `target/surefire-reports/TEST-*.xml`, no de estimaciones.

| Módulo | Tests | Clases de test | Fallos | Errores | Omitidos |
|---|---:|---:|---:|---:|---:|
| `commsHmi` | 166 | 11 | 0 | 0 | 0 |
| `ConfigPhoebus` | 0 | 0 | 0 | 0 | 0 |
| `HMIPanelSubprojectCategory` | 64 | 6 | 0 | 0 | 0 |
| `HMIPanelSubproject` | 17 | 1 | 0 | 0 | 0 |
| **Total** | **247** | **18** | **0** | **0** | **0** |

## Diferencias con las estimaciones del plan

El plan estimaba «~260 tests» y «~180 tests en `commsHmi`». Los valores reales
son **247** y **166**, unos puntos por debajo. Se usa el valor real como
referencia; las estimaciones del plan no son una medida válida de éxito.

## Tareas marcadas como omitidas

Estas tareas del plan se omiten porque sus condiciones previas ya están
satisfechas en la rama de partida:

| Tarea | Motivo |
|---|---|
| **T2.5** | `commsHmi` ya es un módulo NBM con el jar embebido vía `X-Class-Path` y sin dependencias de NetBeans. Un spike previo verificó que el empaquetado `nbm` funciona. |
| **T6.2** | `HMIPanelImportAction` ya usa `sealed interface`, `record`, `ProgressUtils`, filtro de extensión y `Logger`. |
| **T6.5** | El aviso de formatos desalineados ya se emite al abrir el importador, no al pulsar «Importar». |

## Tarea en pausa

| Tarea | Motivo |
|---|---|
| **T1.12** | Requiere decidir si `DisplayNodeFactory` aplica a todas las categorías. Prohibido modificarlo hasta que el responsable lo confirme. Duda registrada en `OPEN_QUESTIONS.md`. |
