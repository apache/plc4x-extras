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
 * The GUI definition of the qooxdoo unit test runner.
 *
 * @asset(qx/icon/Tango/22/actions/media-playback-start.png)
 * @asset(qx/icon/Tango/16/actions/edit-find.png)
 * @asset(qx/icon/Tango/22/actions/go-previous.png)
 * @asset(qx/icon/Tango/22/actions/go-next.png)
 * @asset(qx/icon/Tango/22/actions/edit-redo.png)
 * @asset(qx/icon/Tango/22/actions/edit-clear.png)
 * @asset(qx/icon/Tango/22/actions/application-exit.png)
 * @asset(qx/icon/Tango/22/apps/utilities-color-chooser.png)
 * @asset(qx/icon/Tango/22/apps/office-spreadsheet.png)
 * @asset(qx/icon/Tango/22/apps/utilities-log-viewer.png)
 * @asset(qx/icon/Tango/22/apps/internet-web-browser.png)
 * @asset(qx/icon/Tango/22/mimetypes/executable.png)
 * @asset(qx/icon/Tango/22/actions/help-contents.png)
 * @asset(qx/icon/Tango/22/actions/help-about.png)
 * @asset(qx/icon/Tango/22/actions/media-seek-forward.png)
 * @asset(qx/icon/Tango/22/mimetypes/text-html.png)
 * @asset(qx/icon/Tango/22/actions/dialog-ok.png)
 * @asset(qx/icon/Tango/22/places/folder-open.png)
 * @asset(qx/icon/Tango/22/actions/help-about.png)
 * @asset(qx/icon/Tango/22/actions/view-refresh.png)
 * @asset(qx/icon/Tango/22/actions/process-stop.png)
 * @asset(qx/icon/Tango/22/apps/internet-feed-reader.png)
 * @asset(qx/icon/Tango/22/actions/dialog-cancel.png)
 * @asset(qx/icon/Tango/22/places/folder.png)
 * 
 * @ignore(qx.$$appRoot)
 * @tag noPlayground
 */
qx.Class.define("org.apache.plc4x.ui.view.DefaultLayout", {
    extend: qx.ui.container.Composite,
 
    construct() {
        
        super();

        this.__menuItemStore = {};

        // Configure layout
        var layout = new qx.ui.layout.VBox();
        this.setLayout(layout); 
        
        var mainSplitPane = new qx.ui.splitpane.Pane("vertical");
        //splitpane.setDecorator("main");        
        
        // Header
//        this.add(this._createHeader());  
        
        // Data
        this.widgets = {};
        this.tests = {};
        this.__currentTheme = "qx.theme.Indigo";        
        
        this.__menuBar = this.__makeMenuBar();        
        this.add(this.__menuBar);   
        
        // Main Split Pane
        var mainsplit = new qx.ui.splitpane.Pane("horizontal");
        mainsplit.setAppearance("app-splitpane");
        this.mainsplit = mainsplit;

        var infosplit = new qx.ui.splitpane.Pane("horizontal");
        infosplit.setDecorator(null);
        this._infosplit = infosplit;
        


        //this.add(mainsplit, { flex: 1 });
        
        // tree side
        var leftComposite = (this._leftComposite = new qx.ui.container.Composite());
        leftComposite.setLayout(new qx.ui.layout.VBox(3));
        mainsplit.add(leftComposite, 0);

        // search
        var searchComposlite = new qx.ui.container.Composite();
        searchComposlite.setLayout(new qx.ui.layout.HBox(3));
        searchComposlite.setAppearance("textfield");
        leftComposite.add(searchComposlite);

        var searchIcon = new qx.ui.basic.Image("icon/16/actions/edit-find.png");
        searchComposlite.add(searchIcon);

        this._searchTextField = new qx.ui.form.TextField();
        this._searchTextField.setLiveUpdate(true);
        this._searchTextField.setAppearance("widget");
        this._searchTextField.setPlaceholder("Filter...");  
        
        searchComposlite.add(this._searchTextField, { flex: 1 });
        
        // create the status of the tree
        this._status = new qx.ui.basic.Label("");
        this._status.setAppearance("widget");
        this._status.setWidth(80);
        this._status.setTextAlign("right");
        searchComposlite.add(this._status);

        mainsplit.add(infosplit, 1);
        this._tree = this.__makeTree();
        leftComposite.add(this._tree, { flex: 1 });
//        this.__makeUrlMenu();

        this._demoView = this.__makeDemoView();

        if (qx.core.Environment.get("qx.contrib") == false) {
          infosplit.add(this._demoView, 2);
        }

        var htmlView = (this.__htmlView = this.__makeHtmlCodeView());
//        var jsView = (this.__jsView = this.__makeJsCodeView());
        var logView = (this.__logView = new qxl.logpane.LogPane());
        logView.setDecorator(null);

        var stack = (this.__stack = new qx.ui.container.Stack());
        stack.setDecorator("main");
        stack.add(htmlView);
//        stack.add(jsView);
        stack.add(logView);

        infosplit.add(stack, 1);
        stack.resetSelection();
        stack.exclude();   
        
      var topWidget = new qx.ui.form.TextArea("Flex:1");
      topWidget.setDecorator(null);
      topWidget.setWrap(true);
      mainsplit.add(topWidget, 3); 
      mainsplit.setMinHeight(700);
      
        mainSplitPane.add(mainsplit, { flex: 1 });      
      
     var scroller = new qx.ui.container.Scroll();
     
//      var outerContainer = new qx.ui.container.Composite(
//        new qx.ui.layout.Basic()
//      );
//      //outerContainer.setAllowStretchX(false);
//      
//      scroller.add(outerContainer);
      
      var bottomWidget = new qx.ui.form.TextArea("Flex:2");
      bottomWidget.setDecorator(null);
      bottomWidget.setWrap(true);
   
      
        mainSplitPane.add(new org.apache.plc4x.ui.view.utils.LogTable());      
      
      this.add(mainSplitPane);
                        
    },
  
    members: {
        // ------------------------------------------------------------------------1
        //   CONSTRUCTOR HELPERS
        // ------------------------------------------------------------------------

        _iframe: null,
        __currentTheme: null,
        __logSync: null,
        __logDone: null,
        _tree: null,
        _status: null,
        _searchTextField: null,
        __playgroundButton: null,
        __currentJSCode: null,
        __menuElements: null,
        _versionFilter: null,
        _navPart: null,
        __sobutt: null,
        __viewPart: null,
        __themePart: null,
        __themeMenu: null,
        __menuBar: null,
        _leftComposite: null,
        _infosplit: null,
        _demoView: null,
        __overflowMenu: null,
        __menuItemStore: null,
        __menuViewRadioGroup: null,
        _urlWindow: null,
        __infoWindow: null,
        __stack: null,
        __htmlView: null,
        __jsView: null,
        __logView: null,
        __viewGroup: null,  
        
          
        /**
         * Creates the application header.
         */
        _createHeader() {
          var layout = new qx.ui.layout.HBox();
          var header = new qx.ui.container.Composite(layout);
          header.setAppearance("app-header");

          var title = new qx.ui.basic.Label("Plc4x IDE");
          var version = new qxl.versionlabel.VersionLabel();
          version.setFont("default");

          header.add(title);
          header.add(new qx.ui.core.Spacer(), { flex: 1 });
          //header.add(version);

          return header;
        },  
        
        __makeToolBar() {
          var bar = new qx.ui.toolbar.ToolBar();

          // NAVIGATION BUTTONS
          // -----------------------------------------------------

          // -- run button
          this._runbutton = new qx.ui.toolbar.Button(
            this.tr("Run"),
            "icon/22/actions/media-playback-start.png"
          );
          this._runbutton.addListener("execute", null, this);
          this._runbutton.setToolTipText("Run the selected demo");
          bar.add(this._runbutton);

          // -----------------------------------------------------    
          // DONE
          // -----------------------------------------------------

          return bar;
        },
                        
        __makeMenuBar() {
            var frame = new qx.ui.container.Composite(new qx.ui.layout.Grow());

            var menubar = new qx.ui.menubar.MenuBar();
            menubar.setWidth(600);
            frame.add(menubar);

            var fileMenu = new qx.ui.menubar.Button("File", null, null);
            var editMenu = new qx.ui.menubar.Button("Edit", null, null);
            var searchMenu = new qx.ui.menubar.Button(
            "Search",
            null,
            null
            );
            var viewMenu = new qx.ui.menubar.Button("View", null, null);
            var formatMenu = new qx.ui.menubar.Button(
            "Format",
            null,
            null
            );
            var helpMenu = new qx.ui.menubar.Button("Help", null, null);

            menubar.add(fileMenu);
            menubar.add(editMenu);
            menubar.add(searchMenu);
            menubar.add(viewMenu);
            menubar.add(formatMenu);
            menubar.add(helpMenu);

            return frame;            
        },
        
        __ehIframeLoaded() {
          var fwindow = this._iframe.getWindow();
          var furl = this._iframe.getSource();
          if (furl != null && furl != this.defaultUrl) {
            var url;
            try {
              url = fwindow.location.href;
            } catch (ex) {
              url = window.location.href;
              var splitIndex = url.lastIndexOf("/");
              if (splitIndex != -1) {
                url = url.substring(0, splitIndex + 1);
              }
              url += furl;
            }

            var posHtml = url.indexOf("/demo/") + 6;
            var posSearch = url.indexOf("?");
            posSearch = posSearch == -1 ? url.length : posSearch;
            var split = url.substring(posHtml, posSearch).split("/");
            var div = String.fromCharCode(187);

            if (split.length == 2) {
              var category = split[0];
              category = category.charAt(0).toUpperCase() + category.substring(1);
              var pagename = split[1].replace(".html", "").replace(/_/g, " ");
              pagename = pagename.charAt(0).toUpperCase() + pagename.substring(1);
              var title =
                "qooxdoo " +
                div +
                " Demo Browser " +
                div +
                " " +
                category +
                " " +
                div +
                " " +
                pagename;
            } else {
              var title = "qooxdoo " + div + " Demo Browser " + div + " Start";
            }

            document.title = title;
          }
        },        

        __makeDemoView() {
          var iframe = new qx.ui.embed.Iframe().set({
            nativeContextMenu: true,
          });

          iframe.addListener("load", this.__ehIframeLoaded, this);
          this._iframe = iframe;

          return iframe;
        },        
        
        /**
         * Tree View in Left Pane
         * - only make root node; rest will befilled when iframe has loaded (with
         *   leftReloadTree)
         *
         * @return {var} TODOC
         */
        __makeTree() {
            var tree1 = new qx.ui.tree.Tree();
            var root = new qx.ui.tree.TreeFolder("Plc4x Services");
            tree1.setAppearance("demo-tree");
            tree1.setRoot(root);
            //tree1.setSelection([root]);

            this.tree = this.widgets["treeview.flat"] = tree1;

            tree1.addListener("changeSelection", null, this);
            tree1.addListener(
            "dbltap",
            function (e) {
                qx.event.Timer.once(null, this, 50);
            },
            this
            );

            return tree1;
        },
        
        __makeHtmlCodeView() {
          var f3 = new qx.ui.embed.Html(
            "<div class='script'>The sample source will be displayed here.</div>"
          );
          f3.setOverflow("auto", "auto");
          f3.setFont("monospace");
          f3.setBackgroundColor("white");
          this.widgets["outputviews.sourcepage.html.page"] = f3;

          f3.getContentElement().setAttribute("id", "qx_srcview");
          if (qx.core.Environment.get("device.type") !== "desktop") {
            f3.getContentElement().setStyle("WebkitOverflowScrolling", "touch");
            f3.getContentElement().setStyle("touchAction", "auto");
          }

          return f3;
        },        
        

    },
  
    destruct() {
     
    }    
  
  });  
    


