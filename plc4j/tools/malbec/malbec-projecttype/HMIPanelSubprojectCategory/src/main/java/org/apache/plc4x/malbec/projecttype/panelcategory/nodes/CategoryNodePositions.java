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
package org.apache.plc4x.malbec.projecttype.panelcategory.nodes;

/**
 * Posicion de cada categoria en el arbol del proyecto.
 *
 * <p>Las posiciones viven aqui y no como constante dentro de cada factory por dos motivos.
 * Uno es que la anotacion {@code @NodeFactory.Registration} no puede referenciar una
 * constante declarada en la propia clase que anota, porque seria una referencia adelantada.
 *
 * <p>El otro es que esa anotacion la procesa NetBeans al compilar y no queda en el class con
 * retencion RUNTIME, asi que no se puede leer por reflexion. Al declarar la constante aqui,
 * la anotacion y los tests leen el mismo sitio: no hay forma de que se desincronicen.
 *
 * <p>Los valores son los que tenia cada factory antes de T4.2.
 */
public final class CategoryNodePositions {

    public static final int AVISOS = 80;
    public static final int RECETAS = 90;
    public static final int HISTORIAL = 100;
    public static final int SCRIPTS = 110;
    public static final int INFORMES = 120;
    public static final int TEXTOS_Y_GRAFICOS = 130;
    public static final int USUARIOS_RUNTIME = 140;
    public static final int CONFIGURACION_PANEL = 150;

    private CategoryNodePositions() {
    }
}