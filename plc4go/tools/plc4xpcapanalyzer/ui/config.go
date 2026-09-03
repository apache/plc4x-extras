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

package ui

import (
	"os"
	"path/filepath"
	"slices"
	"time"

	"github.com/apache/plc4x/plc4go/spi/errors"
	"gopkg.in/yaml.v3"

	cliConfig "github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
)

// The session configuration this tool remembers between runs.
//
// It is loaded and saved explicitly rather than in an init function. The version this replaces
// created the configuration directory from a package-level init and panicked when it could
// not, so merely importing the package for a test wrote to the user's home directory and, on a
// machine where os.UserConfigDir fails, took the whole process down before main ran.

// ConfigFileName is the file the session configuration is kept in, inside the tool's directory
// under the user's configuration directory.
const ConfigFileName = "config.yml"

// HistoryLimit is how many recent files and recent commands are remembered. The field names
// below still say "last 10" because that is what the on-disk format calls them.
const HistoryLimit = 10

// Config is what survives between sessions.
type Config struct {
	HostIp  string `yaml:"host_ip"`
	History struct {
		Last10Files    []string `yaml:"last_hosts"`
		Last10Commands []string `yaml:"last_commands"`
	} `yaml:"history"`
	AutoRegisterDrivers []string      `yaml:"auto_register_driver"`
	LastUpdated         time.Time     `yaml:"last_updated"`
	LogLevel            string        `yaml:"log_level"`
	MaxConsoleLines     int           `yaml:"max_console_lines"`
	MaxOutputLines      int           `yaml:"max_output_lines"`
	CliConfigs          AllCliConfigs `yaml:"cli_configs"`
}

// AllCliConfigs is the set of CLI configuration singletons the conf command reflects over.
// The pointers are shared with the cobra layer, so a value set here is the value a run uses.
type AllCliConfigs struct {
	RootConfig    *cliConfig.RootConfig
	AnalyzeConfig *cliConfig.AnalyzeConfig
	ExtractConfig *cliConfig.ExtractConfig
	BacnetConfig  *cliConfig.BacnetConfig
	CBusConfig    *cliConfig.CBusConfig
	PcapConfig    *cliConfig.PcapConfig
}

// CliConfigInstances points at the live singletons.
func CliConfigInstances() AllCliConfigs {
	return AllCliConfigs{
		RootConfig:    &cliConfig.RootConfigInstance,
		AnalyzeConfig: &cliConfig.AnalyzeConfigInstance,
		ExtractConfig: &cliConfig.ExtractConfigInstance,
		BacnetConfig:  &cliConfig.BacnetConfigInstance,
		CBusConfig:    &cliConfig.CBusConfigInstance,
		PcapConfig:    &cliConfig.PcapConfigInstance,
	}
}

// NewConfig returns the defaults a fresh installation starts from.
func NewConfig() Config {
	config := Config{
		MaxConsoleLines: 500,
		MaxOutputLines:  500,
		CliConfigs:      CliConfigInstances(),
	}
	return config
}

// ConfigDir is the directory the session configuration lives in. It returns an error rather
// than panicking, because a tool that cannot find a home directory should still start.
func ConfigDir() (string, error) {
	userConfigDir, err := os.UserConfigDir()
	if err != nil {
		return "", errors.Wrap(err, "error resolving the user configuration directory")
	}
	return filepath.Join(userConfigDir, "plc4xpcapanalyzer"), nil
}

// ConfigPath is the full path of the session configuration file.
func ConfigPath() (string, error) {
	dir, err := ConfigDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, ConfigFileName), nil
}

// LoadConfigFrom reads the configuration at path. A missing file is not an error: it is what a
// first run looks like, and the defaults are correct for it.
func LoadConfigFrom(path string) (Config, error) {
	config := NewConfig()
	file, err := os.Open(path)
	if err != nil {
		if os.IsNotExist(err) {
			return config, nil
		}
		return config, errors.Wrapf(err, "error opening %s", path)
	}
	defer func() { _ = file.Close() }()

	if err := yaml.NewDecoder(file).Decode(&config); err != nil {
		// A corrupt file must not stop the tool starting, so the defaults are returned
		// alongside the error and the caller decides how loudly to complain.
		return NewConfig(), errors.Wrapf(err, "error decoding %s", path)
	}
	// Decoding replaces the pointer set, and a file written by an older version may not carry
	// one at all. Point them back at the live singletons so conf set reaches the real config.
	config.CliConfigs = CliConfigInstances()
	return config, nil
}

// SaveConfigTo writes the configuration to path, creating the directory if it is missing.
func SaveConfigTo(path string, config Config, now time.Time) error {
	config.LastUpdated = now
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return errors.Wrapf(err, "error creating %s", filepath.Dir(path))
	}
	file, err := os.Create(path)
	if err != nil {
		return errors.Wrapf(err, "error creating %s", path)
	}
	defer func() { _ = file.Close() }()

	encoder := yaml.NewEncoder(file)
	if err := encoder.Encode(config); err != nil {
		_ = encoder.Close()
		return errors.Wrapf(err, "error encoding %s", path)
	}
	return errors.Wrapf(encoder.Close(), "error closing %s", path)
}

// RememberFile records a capture in the recent-files list, most recent last, without
// duplicates.
func (c *Config) RememberFile(pcapFile string) {
	c.History.Last10Files = remember(c.History.Last10Files, pcapFile)
}

// RememberCommand records a command in the recent-commands list.
//
// clear and history are skipped, as they were before: they are the two commands whose whole
// purpose is to inspect or reset the session, and remembering them crowds out the commands a
// user actually wants to recall.
func (c *Config) RememberCommand(command string) {
	switch command {
	case "clear", "history":
		return
	}
	c.History.Last10Commands = remember(c.History.Last10Commands, command)
}

// remember appends entry, moving an existing occurrence to the end and dropping the oldest
// once the limit is reached.
func remember(entries []string, entry string) []string {
	if entry == "" {
		return entries
	}
	if index := slices.Index(entries, entry); index >= 0 {
		entries = slices.Delete(entries, index, index+1)
	}
	entries = append(entries, entry)
	if len(entries) > HistoryLimit {
		entries = slices.Delete(entries, 0, len(entries)-HistoryLimit)
	}
	return entries
}

// EnableAutoRegister adds a driver to the set registered at startup.
func (c *Config) EnableAutoRegister(driver string) error {
	if slices.Contains(c.AutoRegisterDrivers, driver) {
		return errors.Errorf("%s already registered for auto register", driver)
	}
	c.AutoRegisterDrivers = append(c.AutoRegisterDrivers, driver)
	return nil
}

// DisableAutoRegister removes a driver from the set registered at startup.
func (c *Config) DisableAutoRegister(driver string) error {
	index := slices.Index(c.AutoRegisterDrivers, driver)
	if index < 0 {
		return errors.Errorf("%s not registered for auto register", driver)
	}
	c.AutoRegisterDrivers = slices.Delete(c.AutoRegisterDrivers, index, index+1)
	return nil
}
