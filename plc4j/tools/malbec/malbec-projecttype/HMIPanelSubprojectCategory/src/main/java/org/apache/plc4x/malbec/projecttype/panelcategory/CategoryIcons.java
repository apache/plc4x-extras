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
 * Rutas de los iconos de las categorias.
 *
 * <p>El mismo literal estaba escrito once en cada constante de {@link HMICategoryDefinition}
 * y otra vez en dos factories, once el total. Con once, cambiar el icono era cambiar once
 * lineas y rezar porque no se olvidara ninguna.
 *
 * <p>Las rutas no llevan barra inicial: es como las espera NetBeans en un {@code iconBase}.
 * {@code CategoryIconsTest} comprueba que cada una exista de verdad en el classpath y que
 * sea un PNG.
 */
public final class CategoryIcons {

    /** Icono de las categorias que no tienen uno propio. */
    public static final String DEFAULT =
            "org/apache/plc4x/malbec/projecttype/hmipanelsubprojectcategory/icon2.png";

    /** Icono de la categoria de Comunicacion. */
    public static final String COMMUNICATION =
            "org/apache/plc4x/malbec/projecttype/hmipanelsubprojectcategory/comm.png";

    private CategoryIcons() {
    }
}