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
package org.apache.plc4x.malbec.projecttype.panelcategory.xml;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Creacion de {@link DocumentBuilderFactory} sin entidades externas (H-08(e)).
 *
 * <p>Un parser sin endurecer permite dos ataques con un fichero XML de origen
 * externo, que es exactamente el caso de uso de este codigo: el usuario elige un
 * fichero de modelo de planta y se parsea.
 *
 * <ul>
 *   <li><b>XXE</b>: una <code>DOCTYPE</code> con una <code>ENTITY</code> apuntando a
 *       <code>file:///etc/passwd</code> o a una URL. Al resolver la entidad se copia
 *       el contenido del fichero o se hace una peticion saliente.</li>
 *   <li><b>Billones de risas</b>: entidades recursivas que agotan la memoria.</li>
 * </ul>
 *
 * <p>La primera linea de defensa es {@code disallow-doctype-decl}, que rechaza
 * cualquier <code>DOCTYPE</code> y con el las entidades. Las demas son defensa en
 * profundidad para parsers que no la soporten, y por eso se ignoran en silencio si
 * el parser las rechaza: si <code>disallow-doctype-decl</code> esta activa, no hay
 * <code>DOCTYPE</code> que pueda llegar a usarlas.
 *
 * <p>Si la proteccion principal no se puede activar, {@link ParserConfigurationException}
 * se propaga. Es deliberado: preferimos no parsear antes que parsear sin proteccion.
 *
 * <p>No se toca {@code namespaceAware}, que se deja como venga. Cambiarlo alteraria la
 * forma de buscar elementos y podria dejar de encontrar los que ya estan en los
 * ficheros del usuario.
 */
public final class XmlSafe {

    private static final String DISALLOW_DOCTYPE =
            "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL =
            "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER =
            "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private XmlSafe() {
    }

    /**
     * Devuelve una fabrica con las entidades externas desactivadas.
     *
     * @throws ParserConfigurationException si no se puede activar
     *         {@code disallow-doctype-decl}, en cuyo caso no se debe parsear
     */
    public static DocumentBuilderFactory newFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Si esto falla, el parseo se cancela: no hay punto medio seguro.
        factory.setFeature(DISALLOW_DOCTYPE, true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        bestEffort(factory, EXTERNAL_GENERAL, false);
        bestEffort(factory, EXTERNAL_PARAMETER, false);
        bestEffort(factory, LOAD_EXTERNAL_DTD, false);

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static void bestEffort(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ex) {
            // Este parser no conoce la opcion. Con disallow-doctype-decl activa no hay
            // DOCTYPE que pueda llegar a estas opciones, asi que se puede seguir.
        }
    }
}
