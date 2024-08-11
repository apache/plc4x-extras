/* 
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/JavaScript.js to edit this template
 */


qx.Class.define("org.apache.plc4x.ui.view.utils.TestLayout", {
    extend: qx.ui.container.Composite,
  
    construct() {
        super();
        
        this.__menuItemStore = {};

        // Configure layout
        var layout = new qx.ui.layout.VBox();
        this.setLayout(layout);

        // Header
        this.add(this._createHeader());
        
        // Data
        this.widgets = {};
        this.tests = {};
        this.__currentTheme = "qx.theme.Indigo";       
        
        // Main Split Pane
        var mainsplit = new qx.ui.splitpane.Pane("horizontal");
        mainsplit.setAppearance("app-splitpane");
        this.mainsplit = mainsplit;

        var infosplit = new qx.ui.splitpane.Pane("horizontal");
        infosplit.setDecorator(null);
        this._infosplit = infosplit;

        this.add(mainsplit, { flex: 1 });     
        
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

        var filterTimer = new qx.event.Timer(500);
        filterTimer.addListener(
          "interval",
          function (ev) {
            this.filter(this._searchTextField.getValue());
            filterTimer.stop();
          },
          this
        );

        this._searchTextField.addListener(
          "changeValue",
          function (ev) {
            filterTimer.restart();
          },
          this
        );
            
    },
  
    members: {
      
      
    
      
        /**
         * Creates the application header.
         */
        _createHeader() {
          var layout = new qx.ui.layout.HBox();
          var header = new qx.ui.container.Composite(layout);
          header.setAppearance("app-header");

          var title = new qx.ui.basic.Label("Plc4x UI");
          var version = new qxl.versionlabel.VersionLabel();
          version.setFont("default");

          header.add(title);
          header.add(new qx.ui.core.Spacer(), { flex: 1 });
          header.add(version);

          return header;
        },    
        
        __makeCommands() {
          this._cmdObjectSummary = new qx.ui.command.Command("Ctrl+O");
          this._cmdObjectSummary.addListener(
            "execute",
            this.__getObjectSummary,
            this
          );

          this._cmdRunSample = new qx.ui.command.Command("F5");
          this._cmdRunSample.addListener("execute", this.runSample, this);

          this._cmdPrevSample = new qx.ui.command.Command("Ctrl+Left");
          this._cmdPrevSample.addListener("execute", this.playPrev, this);

          this._cmdNextSample = new qx.ui.command.Command("Ctrl+Right");
          this._cmdNextSample.addListener("execute", this.playNext, this);

          this._cmdSampleInOwnWindow = new qx.ui.command.Command("Ctrl+N");
          this._cmdSampleInOwnWindow.addListener(
            "execute",
            this.__openWindow,
            this
          );
        },  
        
        __openWindow() {
          var sampUrl = this._iframe.getSource();
          // remove th query params
          sampUrl = sampUrl.substr(0, sampUrl.indexOf("?"));
          // add the current theme as env setting
          if (qx.core.Environment.get("qx.contrib") == false) {
            sampUrl += "?qxenv:qx.theme:" + this.__currentTheme;
          }
          window.open(sampUrl, "_blank");
        },        
      
    },
  
    destruct() {
     
    },  
  
    });

