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

package browser

import (
	"os"
	"path/filepath"
	"slices"
	"time"

	"github.com/apache/plc4x/plc4go/spi/errors"
	"gopkg.in/yaml.v3"
)

// The on-disk settings, carried over from the previous implementation so that an existing
// ~/.config/plc4xbrowser/config.yml keeps working: the field names and the YAML keys are
// unchanged.
//
// What did change is ownership. The previous version kept the settings in a package-level
// variable, loaded them from an init function and panicked from that init when the user's
// config directory could not be created - which aborts the process before main runs, with a
// stack trace and no explanation. Here the path is resolved by a function that returns an
// error, the settings are a value, and a broken or unreadable file degrades to the defaults.

// HistoryLimit is how many hosts and commands are remembered. It is ten because the YAML keys
// are named for ten and an existing file must keep meaning what it says.
const HistoryLimit = 10

// Config is the persisted state of a browser session.
type Config struct {
	History struct {
		Last10Hosts    []string `yaml:"last_hosts"`
		Last10Commands []string `yaml:"last_commands"`
	} `yaml:"history"`
	AutoRegisterDrivers []string  `yaml:"auto_register_driver"`
	LastUpdated         time.Time `yaml:"last_updated"`
	LogLevel            string    `yaml:"log_level"`
	MaxConsoleLines     int       `yaml:"max_console_lines"`
	MaxOutputLines      int       `yaml:"max_output_lines"`
}

// Default numbers of retained lines, matching the previous implementation.
const (
	defaultMaxConsoleLines = 500
	defaultMaxOutputLines  = 500
)

// NewConfig returns the defaults.
func NewConfig() Config {
	return Config{
		MaxConsoleLines: defaultMaxConsoleLines,
		MaxOutputLines:  defaultMaxOutputLines,
	}
}

// ConfigPath returns the file the settings live in, creating the directory if it does not
// exist. Unlike the code it replaces it reports a failure rather than panicking, because a
// read-only home directory is a reason to run without persistence, not a reason to refuse to
// start.
func ConfigPath() (string, error) {
	userConfigDir, err := os.UserConfigDir()
	if err != nil {
		return "", errors.Wrap(err, "cannot locate the user config directory")
	}
	dir := filepath.Join(userConfigDir, "plc4xbrowser")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", errors.Wrapf(err, "cannot create %s", dir)
	}
	return filepath.Join(dir, "config.yml"), nil
}

// LoadConfig reads the settings from path.
//
// A missing file is not an error: it is the first run. A malformed one is reported, but the
// defaults are still returned, so a hand-edited file with a typo in it cannot lock the user
// out of the tool.
func LoadConfig(path string) (Config, error) {
	config := NewConfig()
	data, err := os.ReadFile(path) //nolint:gosec // the path comes from ConfigPath or a test.
	if err != nil {
		if os.IsNotExist(err) {
			return config, nil
		}
		return config, errors.Wrapf(err, "cannot read %s", path)
	}
	if err := yaml.Unmarshal(data, &config); err != nil {
		return NewConfig(), errors.Wrapf(err, "cannot parse %s", path)
	}
	// A file written by an older version, or by hand, may leave these at zero, which would
	// mean "retain nothing" rather than "unset".
	if config.MaxConsoleLines <= 0 {
		config.MaxConsoleLines = defaultMaxConsoleLines
	}
	if config.MaxOutputLines <= 0 {
		config.MaxOutputLines = defaultMaxOutputLines
	}
	return config, nil
}

// Save writes the settings to path.
func (c *Config) Save(path string) error {
	c.LastUpdated = time.Now()
	data, err := yaml.Marshal(c)
	if err != nil {
		return errors.Wrap(err, "cannot encode the configuration")
	}
	// Truncating: the previous implementation opened with O_RDWR|O_CREATE and no O_TRUNC, so
	// writing a shorter document than the one already there left the tail of the old one
	// behind and produced a file that no longer parses.
	if err := os.WriteFile(path, data, 0o600); err != nil {
		return errors.Wrapf(err, "cannot write %s", path)
	}
	return nil
}

// AddHost records a host in the connect history, most recent last.
func (c *Config) AddHost(host string) {
	if host == "" {
		return
	}
	c.History.Last10Hosts = pushRecent(c.History.Last10Hosts, host)
}

// AddCommand records a command line in the history, most recent last.
//
// clear and history are skipped, as they were before: replaying either from the history is
// never what the user meant, and both would otherwise crowd out the ten slots.
func (c *Config) AddCommand(command string) {
	switch command {
	case "", "clear", "history":
		return
	}
	c.History.Last10Commands = pushRecent(c.History.Last10Commands, command)
}

// EnableAutoRegister marks a protocol for registration at startup.
func (c *Config) EnableAutoRegister(protocol string) error {
	if slices.Contains(c.AutoRegisterDrivers, protocol) {
		return errors.Errorf("%s is already registered for auto register", protocol)
	}
	c.AutoRegisterDrivers = append(c.AutoRegisterDrivers, protocol)
	return nil
}

// DisableAutoRegister unmarks a protocol.
func (c *Config) DisableAutoRegister(protocol string) error {
	index := slices.Index(c.AutoRegisterDrivers, protocol)
	if index < 0 {
		return errors.Errorf("%s is not registered for auto register", protocol)
	}
	c.AutoRegisterDrivers = slices.Delete(c.AutoRegisterDrivers, index, index+1)
	return nil
}

// pushRecent appends value, removing any earlier occurrence and dropping the oldest entries
// once the limit is passed.
func pushRecent(entries []string, value string) []string {
	if index := slices.Index(entries, value); index >= 0 {
		entries = slices.Delete(entries, index, index+1)
	}
	entries = append(entries, value)
	if len(entries) > HistoryLimit {
		entries = slices.Clone(entries[len(entries)-HistoryLimit:])
	}
	return entries
}
