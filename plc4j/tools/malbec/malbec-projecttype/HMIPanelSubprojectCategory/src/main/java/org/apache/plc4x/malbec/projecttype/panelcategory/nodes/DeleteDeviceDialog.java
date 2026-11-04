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

import java.awt.Frame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.SaveResult;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.openide.util.RequestProcessor;
import org.openide.windows.WindowManager;

final class DeleteDeviceDialog {

    private DeleteDeviceDialog() {
    }

    static void delete(String uuid, String name, HMICommunicationModel model, Frame window) {
        if (model == null) {
            return;
        }

        Object[] options = {"Accept", "Cancel"};
        int chosen = JOptionPane.showOptionDialog(window,
                DeviceNodeLabels.deletionMessage(name, model.getComms(uuid)),
                "Delete device", JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, options, options[0]);
        if (chosen != 0) {
            return;
        }

        if (!model.removeDevice(uuid)) {
            JOptionPane.showMessageDialog(window,
                    DeviceNodeLabels.nothingToDeleteMessage(name),
                    "Nothing to delete", JOptionPane.WARNING_MESSAGE);
            return;
        }

        RequestProcessor.getDefault().post(() -> {
            SaveResult result = model.save();
            SwingUtilities.invokeLater(() -> show(window, name, result));
        });
    }

    private static void show(Frame window, String name, SaveResult result) {
        if (!result.xmlSaved()) {
            JOptionPane.showMessageDialog(window,
                    "Could not be deleted '" + name + "'.\n\n" + result.message(),
                    "Error while deleting", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (result.isPartial()) {
            JOptionPane.showMessageDialog(window,
                    DeviceNodeLabels.partialSaveMessage(name),
                    "Partial save", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(window,
                DeviceNodeLabels.correctDeletionMessage(name),
                "Successful deletion", JOptionPane.INFORMATION_MESSAGE);
    }


    static Frame mainWindow() {
        return WindowManager.getDefault().getMainWindow();
    }
}
