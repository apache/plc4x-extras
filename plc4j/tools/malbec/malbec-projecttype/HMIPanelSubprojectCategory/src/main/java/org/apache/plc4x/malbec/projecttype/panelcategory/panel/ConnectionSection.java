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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import java.awt.Component;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceDynamicPanelBuilder;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.UrlDisassembler;

/**
 * La parte del formulario que describe como se conecta el dispositivo:
 * transporte, host, puerto, parametros y la vista previa de la URL.
 *
 * <p>
 * Estaba repartida por {@code CreateDevicePanel}: los controles vivian ahi,
 * pero tambien vivian ahi las reglas de cuando el puerto aplica, que parametros
 * ofrece cada transporte y como se reconstruye la URL. Aqui estan los dos
 * juntos, que es lo que hace falta para razonar sobre ellos.
 *
 * <p>
 * No sabe nada de marca, modelo ni del arbol S88, y no guarda nada: solo
 * mantiene lo que el usuario ve y se lo devuelve al panel cuando hace falta.
 */
/**
 * Los controles se exponen como campos de solo lectura del paquete.
 *
 * <p>
 * El panel los coloca en su rejilla y los consulta en sus propias reglas, asi
 * que esconderlos tras ocho getters identicos no aporta nada: solo anade
 * indireccion. Se declara el campo privado y se lee a traves de este, que es la
 * forma de dejar claro que desde fuera no se pueden cambiar.
 */
final class ConnectionSection {

    private final JLabel lblHost = new JLabel("Host/IP:");
    private final JLabel lblPort = new JLabel("Port:");
    private final JTextField txtHost = new JTextField();
    private final JComboBox<String> cbTransport = new JComboBox<>();
    private final JTextField txtPort = new JTextField();
    private final JComboBox<String> cbParameter = new JComboBox<>();
    private final JTextField txtParamValue = new JTextField();
    private final JTextField txtUrlPreview = new JTextField();

    /**
     * El builder del protocolo activo, o null si todavia no se ha elegido
     * modelo.
     */
    private DeviceDynamicPanelBuilder activeBuilder;

    ConnectionSection() {
        txtUrlPreview.setEditable(false);
        txtParamValue.setEnabled(false);
        cbTransport.setEditable(true);
        lblPort.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));
    }

    /**
     * Deja la seccion como recien abierta, sin builder.
     *
     * <p>
     * Se usa al vaciar el formulario entero y al cambiar de marca: lo que habia
     * escrito era de otro dispositivo, asi que no puede quedarse puesto. Las
     * etiquetas vuelven a su texto y a su estado inicial, no solo se vacian los
     * campos, porque si no la siguiente conexion aparece con el nombre del
     * transporte anterior.
     */
    void reiniciar() {
        setBuilder(null);
        txtHost.setText("");
        lblHost.setText("Host/IP:");
        cbTransport.removeAllItems();
        txtPort.setText("");
        lblPort.setEnabled(true);
        txtPort.setEnabled(true);
        cbParameter.removeAllItems();
        txtParamValue.setText("");
        txtParamValue.setEnabled(false);
        txtUrlPreview.setText("");
    }

    /**
     * Como se llama el campo de host ahora mismo.
     *
     * <p>
     * La etiqueta la cambia el builder segun el transporte: no siempre es
     * "Host/IP". El panel la usa para nombrar el campo que falta en la lista de
     * avisos, y por eso tiene que leerse de aqui y no de una constante suya.
     */
    String etiquetaHost() {
        return lblHost.getText().replace(":", "");
    }

    String host() {
        return txtHost.getText().trim();
    }

    String puerto() {
        return txtPort.getText().trim();
    }

    /**
     * Escucha los cinco controles de la conexion.
     *
     * <p>
     * Cada cambio reconstruye la vista previa de la URL, que es lo que el
     * usuario usa para comprobar si lo que ha escrito es lo que quiere. El
     * editor del transporte tambien se escucha aparte del desplegable porque es
     * editable: escribir a mano tiene que actualizar la URL igual que elegir de
     * la lista.
     */
    void conectarListeners() {
        cbParameter.addActionListener(e -> {
            String key = (String) cbParameter.getSelectedItem();
            if (key == null) {
                txtParamValue.setEnabled(false);
                txtParamValue.setText("");
                return;
            }
            txtParamValue.setEnabled(true);
            txtParamValue.setText(activeBuilder == null
                    ? "" : activeBuilder.getParameterDefaults().get(key));
        });

        txtHost.getDocument().addDocumentListener(
                new SimpleDocumentListener(this::refrescarUrlPreview));

        cbTransport.addActionListener(e -> alCambiarTransporte());
        JComponent transportEditor = (JComponent) cbTransport.getEditor().getEditorComponent();
        if (transportEditor instanceof JTextField transportField) {
            transportField.getDocument().addDocumentListener(
                    new SimpleDocumentListener(this::refrescarUrlPreview));
        }

        txtPort.getDocument().addDocumentListener(
                new SimpleDocumentListener(this::refrescarUrlPreview));
    }

    /**
     * Avisa de cada cambio del documento, sin distinguir insercion de borrado.
     */
    private static final class SimpleDocumentListener
            implements javax.swing.event.DocumentListener {

        private final Runnable action;

        SimpleDocumentListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }
    }

    /**
     * Los ocho controles, que el panel coloca en su rejilla.
     *
     * <p>
     * Se devuelven por metodo y no como campos porque asi se ve que el panel no
     * puede sustituirlos por otros: si mañana se cambiasen por dentro, el sitio
     * que los coloca dejaria de compilar.
     */
    JLabel lblHost() {
        return lblHost;
    }

    JLabel lblPort() {
        return lblPort;
    }

    JTextField txtHost() {
        return txtHost;
    }

    JComboBox<String> cbTransport() {
        return cbTransport;
    }

    JTextField txtPort() {
        return txtPort;
    }

    JComboBox<String> cbParameter() {
        return cbParameter;
    }

    JTextField txtParamValue() {
        return txtParamValue;
    }

    JTextField txtUrlPreview() {
        return txtUrlPreview;
    }

    /**
     * Los tres botones de anadir, quitar y limpiar parametros, con su
     * comportamiento.
     */
    JPanel parameterButtons() {
        JButton btnAddParam = new JButton("Add");
        JButton btnRemoveParam = new JButton("Remove");
        JButton btnClear = new JButton("Clean");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        buttons.add(btnAddParam);
        buttons.add(btnRemoveParam);
        buttons.add(btnClear);

        btnAddParam.addActionListener(e -> {
            if (activeBuilder == null) {
                return;
            }
            String key = (String) cbParameter.getSelectedItem();
            if (key == null) {
                return;
            }
            activeBuilder.addParameter(key, txtParamValue.getText().trim());
            refrescarUrlPreview();
        });
        btnRemoveParam.addActionListener(e -> {
            if (activeBuilder != null) {
                activeBuilder.removeLastParameter();
            }
            refrescarUrlPreview();
        });
        btnClear.addActionListener(e -> {
            if (activeBuilder != null) {
                activeBuilder.clearParameters();
            }
            refrescarUrlPreview();
        });
        return buttons;
    }

    // ---- reglas ------------------------------------------------------------
    /**
     * Elige el builder del modelo y recarga transportes y parametros.
     */
    void cargarBuilderParams(DeviceModel modelo) {
        cbParameter.removeAllItems();
        txtParamValue.setEnabled(false);
        txtParamValue.setText("");
        if (activeBuilder == null) {
            refrescarUrlPreview();
            return;
        }
        activeBuilder.initForModel(modelo);

        cbTransport.removeAllItems();
        cbTransport.setEditable(true);
        for (String option : activeBuilder.getTransportOptions()) {
            cbTransport.addItem(option);
        }
        cbTransport.setSelectedItem(activeBuilder.getDefaultTransport());
        if (cbTransport.getSelectedItem() == null && cbTransport.getItemCount() > 0) {
            cbTransport.setSelectedIndex(0);
        }
        alCambiarTransporte();
    }

    void setBuilder(DeviceDynamicPanelBuilder activeBuilder) {
        this.activeBuilder = activeBuilder;
    }

    DeviceDynamicPanelBuilder builder() {
        return activeBuilder;
    }

    /**
     * El transporte que cuenta: lo que el usuario haya tecleado si el
     * desplegable es editable, y si no lo que este elegido.
     *
     * <p>
     * El desplegable es editable a proposito porque hay transportes libres que
     * no vienen en la lista, y entonces lo escrito es lo que vale.
     */
    String transporteActual() {
        if (cbTransport.isEditable()) {
            Component editor = cbTransport.getEditor().getEditorComponent();
            if (editor instanceof JTextField campo) {
                String tipeado = campo.getText().trim();
                if (!tipeado.isEmpty()) {
                    return tipeado;
                }
            }
        }
        Object selected = cbTransport.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    void alCambiarTransporte() {
        if (activeBuilder == null) {
            return;
        }
        String transportValue = transporteActual();
        activeBuilder.setTransport(transportValue);

        lblHost.setText(activeBuilder.getHostLabel());
        boolean portApplicable = activeBuilder.isPortApplicable();
        lblPort.setEnabled(portApplicable);
        txtPort.setEnabled(portApplicable);
        if (!portApplicable) {
            txtPort.setText("");
        }

        refrescarCatalogoParametros(transportValue);
        refrescarUrlPreview();
    }

    /**
     * Los parametros que ofrece el transporte, conservando el que ya estuviera
     * elegido.
     *
     * <p>
     * Se conserva porque el usuario elige el parametro antes que su valor: si
     * al cambiar de transporte se perdiera la seleccion, habria que volver a
     * elegirlo en cada cambio.
     */
    private void refrescarCatalogoParametros(String transport) {
        if (activeBuilder == null) {
            cbParameter.removeAllItems();
            txtParamValue.setEnabled(false);
            txtParamValue.setText("");
            return;
        }
        LinkedHashMap<String, String> catalog = activeBuilder.getParameterDefaults(transport);
        activeBuilder.retainParameters(catalog.keySet());

        String selectedKey = (String) cbParameter.getSelectedItem();
        cbParameter.removeAllItems();
        for (String key : catalog.keySet()) {
            cbParameter.addItem(key);
        }
        if (selectedKey != null && catalog.containsKey(selectedKey)) {
            cbParameter.setSelectedItem(selectedKey);
        }
        Object current = cbParameter.getSelectedItem();
        if (current == null) {
            txtParamValue.setEnabled(false);
            txtParamValue.setText("");
        } else {
            txtParamValue.setEnabled(true);
            txtParamValue.setText(activeBuilder.getParameters()
                    .getOrDefault(current.toString(), catalog.getOrDefault(current.toString(), "")));
        }
    }

    void refrescarUrlPreview() {
        if (activeBuilder == null) {
            txtUrlPreview.setText("");
            return;
        }
        activeBuilder.setHost(txtHost.getText().trim());
        activeBuilder.setTransport(transporteActual());
        activeBuilder.setPort(txtPort.getText().trim());
        txtUrlPreview.setText(activeBuilder.getSpecificParametersAsString());
    }

    /**
     * Vuelca una URL guardada en los campos.
     *
     * <p>
     * Una URL que no se puede interpretar no se descarta en silencio: se deja
     * tal cual en la vista previa y se avisa, porque el usuario tiene que
     * revisarla antes de guardar y no puede hacerlo si los campos quedan vacios
     * sin explicación.
     */
    void cargarConexion(String url, java.awt.Component padre) {
        if (activeBuilder == null || url == null || url.isBlank()) {
            return;
        }
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble(url);
        if (partes == null) {
            txtUrlPreview.setText(url.trim());
            JOptionPane.showMessageDialog(padre,
                    "No se pudo interpretar la URL de conexión guardada:\n\n" + url.trim()
                    + "\n\nRevisá host, puerto y parámetros antes de guardar.",
                    "Conexión a revisar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        cbTransport.setSelectedItem(partes.transport());
        alCambiarTransporte();
        txtHost.setText(partes.host());
        txtPort.setText(partes.port());
        activeBuilder.clearParameters();
        partes.params().forEach(activeBuilder::addParameter);
        refrescarUrlPreview();
    }

    void limpiarConexion() {
        txtHost.setText("");
        txtPort.setText("");
        if (activeBuilder != null) {
            activeBuilder.clearParameters();
        }
        cbTransport.removeAllItems();
        cbParameter.removeAllItems();
        txtParamValue.setEnabled(false);
        txtParamValue.setText("");
        txtUrlPreview.setText("");
    }

    /**
     * La URL tal como se guardaria, que es lo que comprueban las pruebas.
     */
    String urlActual() {
        refrescarUrlPreview();
        return txtUrlPreview.getText();
    }

}
