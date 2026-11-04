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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.junit.jupiter.api.Test;

class PvFormPanelTest {

    private PvFormPanel formWithArea() {
        PvFormPanel form = new PvFormPanel();
        form.refreshState(true, false);
        return form;
    }

    @Test
    void s88ModeIsTheDefaultAndCustomNameIsDisabled() {
        PvFormPanel form = formWithArea();

        assertTrue(form.fromS88Radio().isSelected());
        assertTrue(form.variableCombo().isEnabled());
        assertFalse(form.customNameField().isEnabled());
        assertFalse(form.quantitySpinner().isEnabled());
    }

    @Test
    void choosingCustomNameSwapsTheControls() {
        PvFormPanel form = formWithArea();

        form.customNameRadio().doClick();

        assertTrue(form.customNameRadio().isSelected());
        assertFalse(form.variableCombo().isEnabled());
        assertTrue(form.customNameField().isEnabled());
        assertTrue(form.quantitySpinner().isEnabled());
    }

    @Test
    void goingBackToS88ClearsTheNameAndDisablesQuantity() {
        PvFormPanel form = formWithArea();
        form.customNameRadio().doClick();
        form.customNameField().setText("Nivel");

        form.fromS88Radio().doClick();

        assertTrue(form.fromS88Radio().isSelected());
        assertEquals("", form.customNameField().getText());
        assertTrue(form.variableCombo().isEnabled());
        assertFalse(form.customNameField().isEnabled());
        assertFalse(form.quantitySpinner().isEnabled());
    }

    @Test
    void customNameIsLimitedToTwentyCharacters() {
        PvFormPanel form = formWithArea();

        form.customNameField().setText("n".repeat(40));

        assertEquals(WizardUi.MAX_NOMBRE, form.customNameField().getText().length());
    }

    @Test
    void quantitySpinnerDefaultsToOneAndRangesFromOneToHundred() {
        PvFormPanel form = formWithArea();
        JSpinner spinner = form.quantitySpinner();

        assertEquals(1, ((Number) spinner.getValue()).intValue());
        SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
        assertEquals(1, model.getMinimum());
        assertEquals(100, model.getMaximum());
    }

    @Test
    void creativeCheckIsDisabledInS88AndWithoutAByteArea() {
        PvFormPanel form = formWithArea();

        assertFalse(form.creativeCheck().isEnabled(), "en S88 no aplica");

        form.customNameRadio().doClick();
        assertFalse(form.creativeCheck().isEnabled(),
                "sin un area BYTE disponible sigue deshabilitado");
    }

    @Test
    void creativeCheckIsEnabledOnlyInCustomModeOfAByteArea() {
        PvFormPanel form = formWithArea();

        form.setCreativeAvailable(true);
        form.customNameRadio().doClick();
        assertTrue(form.creativeCheck().isEnabled());

        form.creativeCheck().doClick();
        assertTrue(form.isCreative());

        form.fromS88Radio().doClick();
        assertFalse(form.creativeCheck().isSelected(),
                "al volver a S88 se apaga el modo creativo");
    }

    @Test
    void customNameIsEditableWhileEditingACustomPv() {
        PvFormPanel form = new PvFormPanel();
        form.customNameRadio().doClick();

        form.refreshState(true, true);

        assertTrue(form.customNameField().isEnabled());
    }

    @Test
    void customNameStaysDisabledWhileEditingAnS88Pv() {
        PvFormPanel form = new PvFormPanel();

        form.refreshState(true, true);

        assertFalse(form.customNameField().isEnabled());
    }
}
