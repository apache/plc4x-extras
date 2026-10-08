/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.projecttype.panelcategory;

/**
 * Los identificadores y nombres de fichero que el plugin comparte con NetBeans.
 *
 * <p>Estaban escritos como literales repartidos por el codigo, y el caso de
 * {@code category.cfg} era el peor: aparecia en cuatro ficheros con tres nombres distintos
 * ({@code CATEGORY_SUBPROJECT_FILE}, {@code CATEGORY_FILE} y {@code PROJECT_FILE}). Cuatro
 * copias de un nombre del que depende que NetBeans reconozca la carpeta, y cambiarlo exige
 * acordarse de las cuatro.
 *
 * <p>Todos los valores son cadenas constantes para poder usarse tambien dentro de anotaciones,
 * que solo admiten expresiones constantes.
 *
 * <p>Cambiar cualquiera de estos valores cambia el proyecto que NetBeans ya tiene en disco:
 * un proyecto creado antes seguiría buscando el fichero con el nombre antiguo. Por eso los
 * valores no se tocan sin motivo, y {@code ProjectIdsTest} los fija.
 */
public final class ProjectIds {

    /**
     * Tipo de proyecto de una categoria de HMI.
     *
     * <p>Lo leen las anotaciones {@code @NodeFactory.Registration} y
     * {@code @ProjectFactory}. Si cambia, NetBeans dejaria de reconocer los proyectos ya
     * creados.
     */
    public static final String CATEGORY_PROJECT_TYPE = "org-apache-plc4x-category";

    /** Tipo de proyecto de un panel HMI. */
    public static final String PANEL_PROJECT_TYPE = "org-apache-plc4x-panel";

    /** Fichero que marca una carpeta como categoria, y la da por valida. */
    public static final String CATEGORY_MARKER_FILE = "category.cfg";

    /** Fichero de configuracion de comunicaciones. */
    public static final String COMMUNICATION_CONFIG_FILE = "comunicacion.xml";

    private ProjectIds() {
    }
}