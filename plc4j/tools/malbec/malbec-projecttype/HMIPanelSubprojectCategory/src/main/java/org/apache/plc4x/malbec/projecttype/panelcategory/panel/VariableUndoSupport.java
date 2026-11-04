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

import java.util.List;
import java.util.Set;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.undo.AbstractUndoableEdit;
import org.openide.awt.UndoRedo;

final class VariableUndoSupport {

    private static final int UNDO_LIMIT = 100;
    private static final String DEFAULT_EDIT_NAME = "Edit variables";

    private final VariableTablePanel tablePanel;
    private final Set<String> deletedPvUuids;
    private final UndoRedo.Manager undoManager = new UndoRedo.Manager();
    private final TableModelListener tableListener = this::tableChanged;

    private UndoState lastState;
    private boolean ready;
    private boolean suspended;

    VariableUndoSupport(VariableTablePanel tablePanel, Set<String> deletedPvUuids) {
        this.tablePanel = tablePanel;
        this.deletedPvUuids = deletedPvUuids;
        undoManager.setLimit(UNDO_LIMIT);
        tablePanel.table().getModel().addTableModelListener(tableListener);
    }

    UndoRedo getUndoRedo() {
        return undoManager;
    }

    void record(String name, Runnable action) {
        if (!ready) {
            action.run();
            return;
        }

        UndoState before = captureState();
        boolean wasSuspended = suspended;
        suspended = true;
        try {
            action.run();
        } finally {
            suspended = wasSuspended;
        }

        UndoState after = captureState();
        if (!before.equals(after)) {
            push(name, before, after);
        }
    }

    void withoutRecording(Runnable action) {
        boolean wasSuspended = suspended;
        suspended = true;
        try {
            action.run();
        } finally {
            suspended = wasSuspended;
        }
        if (ready) {
            lastState = captureState();
        }
    }

    void startExternalChange() {
        ready = false;
    }

    void markSaved() {
        discardHistory();
    }

    void discardHistory() {
        undoManager.discardAllEdits();
        lastState = captureState();
        ready = true;
    }

    void close() {
        undoManager.discardAllEdits();
        lastState = captureState();
    }

    private void tableChanged(TableModelEvent event) {
        if (!ready || suspended) {
            return;
        }

        UndoState after = captureState();
        if (lastState == null) {
            lastState = after;
            return;
        }
        if (after.equals(lastState)) {
            return;
        }

        String name = event.getType() == TableModelEvent.UPDATE
                ? "Edit cell" : DEFAULT_EDIT_NAME;
        push(name, lastState, after);
    }

    private void push(String name, UndoState before, UndoState after) {
        undoManager.undoableEditHappened(
                new UndoableEditEvent(this, new SnapshotEdit(name, before, after)));
        lastState = after;
    }

    private UndoState captureState() {
        return new UndoState(tablePanel.rows(), Set.copyOf(deletedPvUuids));
    }

    private void restoreState(UndoState state) {
        boolean wasReady = ready;
        ready = false;
        try {
            tablePanel.setRows(state.rows());
            deletedPvUuids.clear();
            deletedPvUuids.addAll(state.deletedPvUuids());
            lastState = state;
        } finally {
            ready = wasReady;
        }
    }

    private record UndoState(
            List<CreateVariableController.Row> rows,
            Set<String> deletedPvUuids) {
    }

    private final class SnapshotEdit extends AbstractUndoableEdit {

        private final String name;
        private final UndoState before;
        private final UndoState after;

        private SnapshotEdit(String name, UndoState before, UndoState after) {
            this.name = name;
            this.before = before;
            this.after = after;
        }

        @Override
        public String getPresentationName() {
            return name;
        }

        @Override
        public void undo() {
            super.undo();
            restoreState(before);
        }

        @Override
        public void redo() {
            super.redo();
            restoreState(after);
        }
    }
}
