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

/**
 * @asset(qx/icon/Tango/16/apps/office-calendar.png)
 * @tag noPlayground
 */
qx.Class.define("org.apache.plc4x.ui.view.utils.LogTable", {
    extend: qx.ui.table.Table,
    
    construct() {
        super();
        //this.setLayout(new qx.ui.layout.Basic());
        //this.add(this._createTable());
          // Create the initial data
          this._rowData = {};
          
          this._createTable();
          
        this._updateTimer = new qx.event.Timer(6000);   
        
        this._updateTimer.addListener(
          "interval",
            function (ev) {
                this._update();
                this._updateTimer.restart();
            },
          this
        );  

        this._updateTimer.start();
       
    },
    
    members: {
        
        _tableModel: null, 
        _rowData: null,
        _updateTimer : null,
        __table: null,    
        
        _createTable() {
          // Create the initial data

          // table model
          this._tableModel =  new qx.ui.table.model.Simple();
          this._tableModel.setColumns(["Type", "Date", "Time", "Source","Event"]);

          this._rowData = this.createRandomRows(1);

          this._tableModel.setData(this._rowData);
          this._tableModel.setColumnEditable(1, false);
          this._tableModel.setColumnEditable(2, false);
          this._tableModel.setColumnSortable(3, false);
          
          this.setTableModel(this._tableModel);

          // table
          var table = this;

          table
            .set({
                statusBarVisible : false,
                decorator: null,
            });

          table
            .getSelectionModel()
            .setSelectionMode(
              qx.ui.table.selection.Model.MULTIPLE_INTERVAL_SELECTION
            );

          var tcm = table.getTableColumnModel();

          // use a different header renderer
          tcm.setHeaderCellRenderer(
            2,
            new qx.ui.table.headerrenderer.Icon(
              "icon/16/apps/office-calendar.png",
              "Date"
            )
          );
        },     
            
        _update() {
            var rowData = this.createRandomRows(1);
            this._tableModel.addRows(rowData);
            this.info("10 rows added");
            //this._updateTimer.restart();
        },  
        
        createRandomRows(rowCount) {
          var rowData = [];
          var now = new Date().getTime();
          var dateRange = 400 * 24 * 60 * 60 * 1000; // 400 days
          for (var row = 0; row < rowCount; row++) {
            var date = new Date(now + Math.random() * dateRange - dateRange / 2);
            rowData.push([
              this.nextId++,
              Math.random() * 10000,
              date,
              Math.random() > 0.5,
              "Esto es un evento"
            ]);
          }
          return rowData;
        },           
        
    },

    
 
    
    destruct() {
      
    }    
 
 
    
    
});
