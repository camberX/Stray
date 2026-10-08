package main

import (
	"os"
	"path/filepath"
	"regexp"
	"time"
)

// Deletes one leftover Stray jar. The updater starts this when Windows still
// has the old jar open. It retries about once a second until the file is gone.
var allowedName = regexp.MustCompile(`(?i)^(stray|voidmark|eisenmann)([-.][\w.+-]*)?\.(jar|jar\.old|jar\.disabled|jar\.part)$`)

func main() {
	if len(os.Args) != 2 {
		os.Exit(2)
	}
	if detach() {
		os.Exit(0)
	}
	path, ok := target(os.Args[1])
	if !ok {
		os.Exit(2)
	}
	deadline := time.Now().Add(8 * time.Hour)
	for {
		if purge(path) {
			return
		}
		if time.Now().After(deadline) {
			return
		}
		time.Sleep(time.Second)
	}
}

func target(raw string) (string, bool) {
	cleaned := filepath.Clean(raw)
	if cleaned == "" || cleaned == "." || !filepath.IsAbs(cleaned) {
		return "", false
	}
	name := filepath.Base(cleaned)
	if name == "." || name == ".." || !allowedName.MatchString(name) {
		return "", false
	}
	return cleaned, true
}

func purge(path string) bool {
	info, err := os.Lstat(path)
	if err != nil {
		return os.IsNotExist(err)
	}
	if info.IsDir() {
		return true
	}
	err = os.Remove(path)
	return err == nil || os.IsNotExist(err)
}
