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
 * Vista minima del arbol de ficheros de un proyecto, para poder probar la
 * deteccion de categoria sin depender de NetBeans (H-05).
 *
 * <p>Antes, {@code getActions()}recebia un {@code FileObject} y lo recorria con
 * recursividad. En el build de pruebas no hay ningun {@code FileSystem} registrado,
 * de modo que un {@code FileObject} de verdad no se puede construir y la deteccion no
 * era verificable. Ademas, buscar un {@code FileObject} para cada uno de los diez
 * ficheros marcador obligaba a recorrer el arbol entero diez veces por cada clic
 * derecho.
 */
interface ArbolProyecto {

    /** Nombre del fichero o carpeta, con su extension. */
    String nombre();

    /** Si es una carpeta y por tanto puede contener hijos. */
    boolean esCarpeta();

    /** Contenido directo. Vacio si no es una carpeta. */
    java.util.List<ArbolProyecto> hijos();
}
