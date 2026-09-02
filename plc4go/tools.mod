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

// tools.mod holds the dev/CI tools invoked from the build so their transitive
// dependencies stay out of go.mod. Use it via `go tool -modfile=tools.mod <name>`.
//
// Unlike plc4x, this module currently has no //go:generate directives, so no tool
// needs to stay in go.mod. If one is added, move that tool back to go.mod: `go
// generate` does not accept -modfile, so a generate directive cannot reach a tool
// declared here.

module github.com/apache/plc4x-extras/plc4go

go 1.27

require (
	charm.land/lipgloss/v2 v2.0.6
	github.com/apache/plc4x/plc4go v0.0.0-20260902093211-931674134ff2
	github.com/fatih/color v1.19.0
	github.com/gdamore/tcell/v2 v2.13.10
	github.com/gopacket/gopacket v1.7.1
	github.com/k0kubun/go-ansi v0.0.0-20180517002512-3bf9e2903213
	github.com/rivo/tview v0.42.0
	github.com/rs/zerolog v1.35.1
	github.com/schollz/progressbar/v3 v3.19.1
	github.com/spf13/cobra v1.8.1
	github.com/spf13/viper v1.19.0
	github.com/stretchr/testify v1.12.1
	gopkg.in/yaml.v3 v3.0.1
)

require (
	github.com/bitfield/gotestdox v0.2.2 // indirect
	github.com/charmbracelet/colorprofile v0.4.3 // indirect
	github.com/charmbracelet/ultraviolet v0.0.0-20260811164956-006e29f97886 // indirect
	github.com/charmbracelet/x/ansi v0.11.8 // indirect
	github.com/charmbracelet/x/term v0.2.2 // indirect
	github.com/charmbracelet/x/termios v0.1.1 // indirect
	github.com/charmbracelet/x/windows v0.2.2 // indirect
	github.com/chigopher/pathlib v0.19.1 // indirect
	github.com/clipperhouse/displaywidth v0.11.0 // indirect
	github.com/clipperhouse/uax29/v2 v2.7.0 // indirect
	github.com/dnephin/pflag v1.0.7 // indirect
	github.com/fsnotify/fsnotify v1.9.0 // indirect
	github.com/gdamore/encoding v1.0.1 // indirect
	github.com/google/shlex v0.0.0-20191202100458-e7afc7fbc510 // indirect
	github.com/hashicorp/hcl v1.0.0 // indirect
	github.com/huandu/xstrings v1.4.0 // indirect
	github.com/iancoleman/strcase v0.3.0 // indirect
	github.com/inconshreveable/mousetrap v1.1.0 // indirect
	github.com/incu6us/goimports-reviser/v3 v3.10.0 // indirect
	github.com/jinzhu/copier v0.4.0 // indirect
	github.com/lucasb-eyer/go-colorful v1.4.1 // indirect
	github.com/magiconair/properties v1.8.9 // indirect
	github.com/mattn/go-colorable v0.1.15 // indirect
	github.com/mattn/go-isatty v0.0.22 // indirect
	github.com/mattn/go-runewidth v0.0.24 // indirect
	github.com/mitchellh/colorstring v0.0.0-20190213212951-d06e56a500db // indirect
	github.com/mitchellh/go-homedir v1.1.0 // indirect
	github.com/mitchellh/mapstructure v1.5.0 // indirect
	github.com/muesli/cancelreader v0.2.2 // indirect
	github.com/pelletier/go-toml/v2 v2.2.3 // indirect
	github.com/pkg/errors v0.9.1 // indirect
	github.com/rivo/uniseg v0.4.7 // indirect
	github.com/sagikazarmark/locafero v0.7.0 // indirect
	github.com/sagikazarmark/slog-shim v0.1.0 // indirect
	github.com/sourcegraph/conc v0.3.0 // indirect
	github.com/spf13/afero v1.12.0 // indirect
	github.com/spf13/cast v1.7.1 // indirect
	github.com/spf13/pflag v1.0.6 // indirect
	github.com/stretchr/objx v0.5.3 // indirect
	github.com/subosito/gotenv v1.6.0 // indirect
	github.com/vektra/mockery/v2 v2.53.2 // indirect
	github.com/xo/terminfo v0.0.0-20220910002029-abceb7e1c41e // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.yaml.in/yaml/v3 v3.0.5 // indirect
	golang.org/x/exp v0.0.0-20250210185358-939b2ce775ac // indirect
	golang.org/x/mod v0.39.0 // indirect
	golang.org/x/net v0.58.0 // indirect
	golang.org/x/sync v0.22.0 // indirect
	golang.org/x/sys v0.47.0 // indirect
	golang.org/x/term v0.45.0 // indirect
	golang.org/x/text v0.41.0 // indirect
	golang.org/x/tools v0.49.0 // indirect
	gopkg.in/ini.v1 v1.67.0 // indirect
	gotest.tools/gotestsum v1.13.0 // indirect
)

tool golang.org/x/tools/cmd/stringer

tool gotest.tools/gotestsum

tool github.com/vektra/mockery/v2

tool github.com/incu6us/goimports-reviser/v3
