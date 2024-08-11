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
 * This is the main application class of "plc4xui"
 *
 * @asset(plc4xui/*)
 */
qx.Class.define("org.apache.plc4x.ui.view.Application", {
    extend : qx.application.Standalone,

    construct() {
        super();

        // Include CSS files
//        var uri = qx.util.ResourceManager.getInstance().toUri(
//        "org/apache/plc4x/ui/css/style.css"
//        );
//        qx.bom.Stylesheet.includeFile(uri);
//        uri = qx.util.ResourceManager.getInstance().toUri(
//            "org/apache/plc4x/ui/css/sourceview.css"
//        );
//        qx.bom.Stylesheet.includeFile(uri);
    },


  /*
  *****************************************************************************
     MEMBERS
  *****************************************************************************
  */

  members : {
    /**
     * This method contains the initial application code and gets called
     * during startup of the application
     *
     * @lint ignoreDeprecated(alert)
     */
    main() {
        // Call super class
        super.main();

        // Enable logging in debug variant
        if (qx.core.Environment.get("qx.debug"))
        {
            // support native logging capabilities, e.g. Firebug for Firefox
            qx.log.appender.Native;
            // support additional cross-browser console. Press F7 to toggle visibility
            qx.log.appender.Console;
        }

        /*
        -------------------------------------------------------------------------
            Below is your actual application code...
        -------------------------------------------------------------------------
        */
        // Initialize the viewer
        this.viewer = new org.apache.plc4x.ui.view.DefaultLayout();
        this.getRoot().add(this.viewer, { edge: 0 });
    },

    // overridden
    finalize() {
      super.finalize();
    },
  },
  
    /*
     * Destructorq1s
     */
  
    destruct() {
        this._disposeObjects("viewer");
    },  
  
});
