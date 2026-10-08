//go:build windows

package main

import (
	"os"
	"strings"
	"syscall"
	"unsafe"
)

const (
	createNoWindow         = 0x08000000
	detachedProcess        = 0x00000008
	createNewProcessGroup  = 0x00000200
	createBreakawayFromJob = 0x01000000
)

// detach starts a second copy outside Minecraft's process tree and job, then
// the first copy exits. The updater kills its own child processes on quit, so
// the copy that deletes the jar has to already be detached.
func detach() bool {
	if os.Getenv("STRAY_PURGE_CHILD") == "1" {
		return false
	}
	exe, err := os.Executable()
	if err != nil || len(os.Args) != 2 {
		return false
	}
	command := quoteArg(exe) + " " + quoteArg(os.Args[1])
	cmd, err := syscall.UTF16PtrFromString(command)
	if err != nil {
		return false
	}
	env, err := childEnv()
	if err != nil {
		return false
	}
	flags := []uint32{
		createNoWindow | detachedProcess | createNewProcessGroup | createBreakawayFromJob,
		createNoWindow | detachedProcess | createNewProcessGroup,
	}
	for _, flag := range flags {
		var si syscall.StartupInfo
		si.Cb = uint32(unsafe.Sizeof(si))
		var pi syscall.ProcessInformation
		err = syscall.CreateProcess(nil, cmd, nil, nil, false, flag, env, nil, &si, &pi)
		if err != nil {
			continue
		}
		_ = syscall.CloseHandle(pi.Thread)
		_ = syscall.CloseHandle(pi.Process)
		return true
	}
	return false
}

func childEnv() (*uint16, error) {
	entries := []string{"STRAY_PURGE_CHILD=1"}
	for _, entry := range os.Environ() {
		if !strings.HasPrefix(strings.ToUpper(entry), "STRAY_PURGE_CHILD=") {
			entries = append(entries, entry)
		}
	}
	var buf []uint16
	for _, entry := range entries {
		wide, err := syscall.UTF16FromString(entry)
		if err != nil {
			continue
		}
		buf = append(buf, wide...)
	}
	buf = append(buf, 0)
	return &buf[0], nil
}

func quoteArg(value string) string {
	if value == "" {
		return `""`
	}
	if !strings.ContainsAny(value, " \t\"") {
		return value
	}
	var out strings.Builder
	out.WriteByte('"')
	slashes := 0
	for _, r := range value {
		switch r {
		case '\\':
			slashes++
		case '"':
			for i := 0; i < slashes*2+1; i++ {
				out.WriteByte('\\')
			}
			out.WriteByte('"')
			slashes = 0
		default:
			for i := 0; i < slashes; i++ {
				out.WriteByte('\\')
			}
			slashes = 0
			out.WriteRune(r)
		}
	}
	for i := 0; i < slashes*2; i++ {
		out.WriteByte('\\')
	}
	out.WriteByte('"')
	return out.String()
}
