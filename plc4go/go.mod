//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

module github.com/apache/plc4x-extras/plc4go

go 1.23.0

toolchain go1.23.1

require (
	github.com/apache/plc4x/plc4go v0.0.0-20241008110309-501f1a6d1550
	github.com/fatih/color v1.17.0
	github.com/gdamore/tcell/v2 v2.7.4
	github.com/gopacket/gopacket v1.3.0
	github.com/k0kubun/go-ansi v0.0.0-20180517002512-3bf9e2903213
	github.com/pkg/errors v0.9.1
	github.com/rivo/tview v0.0.0-20240921122403-a64fc48d7654
	github.com/rs/zerolog v1.33.0
	github.com/schollz/progressbar/v3 v3.16.1
	github.com/spf13/cobra v1.8.1
	github.com/spf13/viper v1.19.0
	github.com/stretchr/testify v1.9.0
	gopkg.in/yaml.v3 v3.0.1
)

require (
	github.com/IBM/netaddr v1.5.0 // indirect
	github.com/cstockton/go-conv v1.0.0 // indirect
	github.com/davecgh/go-spew v1.1.2-0.20180830191138-d8f796af33cc // indirect
	github.com/fsnotify/fsnotify v1.7.0 // indirect
	github.com/gdamore/encoding v1.0.0 // indirect
	github.com/google/uuid v1.6.0 // indirect
	github.com/hashicorp/hcl v1.0.0 // indirect
	github.com/icza/bitio v1.1.0 // indirect
	github.com/inconshreveable/mousetrap v1.1.0 // indirect
	github.com/jacobsa/go-serial v0.0.0-20180131005756-15cf729a72d4 // indirect
	github.com/libp2p/go-reuseport v0.4.0 // indirect
	github.com/lucasb-eyer/go-colorful v1.2.0 // indirect
	github.com/magiconair/properties v1.8.7 // indirect
	github.com/mattn/go-colorable v0.1.13 // indirect
	github.com/mattn/go-isatty v0.0.20 // indirect
	github.com/mattn/go-runewidth v0.0.16 // indirect
	github.com/mitchellh/colorstring v0.0.0-20190213212951-d06e56a500db // indirect
	github.com/mitchellh/mapstructure v1.5.0 // indirect
	github.com/pelletier/go-toml/v2 v2.2.2 // indirect
	github.com/pmezard/go-difflib v1.0.1-0.20181226105442-5d4384ee4fb2 // indirect
	github.com/rivo/uniseg v0.4.7 // indirect
	github.com/sagikazarmark/locafero v0.4.0 // indirect
	github.com/sagikazarmark/slog-shim v0.1.0 // indirect
	github.com/sourcegraph/conc v0.3.0 // indirect
	github.com/spf13/afero v1.11.0 // indirect
	github.com/spf13/cast v1.6.0 // indirect
	github.com/spf13/pflag v1.0.5 // indirect
	github.com/stretchr/objx v0.5.2 // indirect
	github.com/subosito/gotenv v1.6.0 // indirect
	go.uber.org/atomic v1.9.0 // indirect
	go.uber.org/multierr v1.9.0 // indirect
	golang.org/x/exp v0.0.0-20230905200255-921286631fa9 // indirect
	golang.org/x/sys v0.26.0 // indirect
	golang.org/x/term v0.24.0 // indirect
	golang.org/x/text v0.19.0 // indirect
	gopkg.in/ini.v1 v1.67.0 // indirect
)

replace github.com/rivo/tview v0.0.0-20240921122403-a64fc48d7654 => github.com/sruehl/tview v0.0.0-20241008132523-4319215719c9 // Fix for: https://github.com/rivo/tview/issues/1031
