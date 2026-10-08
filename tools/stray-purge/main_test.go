package main

import (
	"path/filepath"
	"testing"
)

func TestTarget(t *testing.T) {
	root := string(filepath.Separator) + "mods" + string(filepath.Separator)
	ok := []string{
		root + "stray.jar",
		root + "stray-dev.jar",
		root + "stray-dev-new.jar",
		root + "stray-1.5.42.jar",
		root + "stray-1.5.42-new.jar",
		root + "stray-1.5.42.jar.old",
		root + "stray-1.5.42.jar.disabled",
		root + "stray-1.5.42.jar.part",
		root + "voidmark-1.2.3.jar",
		root + "eisenmann-1.0.jar",
	}
	for _, path := range ok {
		if _, allowed := target(path); !allowed {
			t.Fatalf("expected allow %s", path)
		}
	}
	bad := []string{
		"stray.jar",
		root + "notes.txt",
		root + "stray.exe",
		root + "notstray.jar",
		root + "stray-1.5.42.jar.old.exe",
	}
	for _, path := range bad {
		if _, allowed := target(path); allowed {
			t.Fatalf("expected reject %s", path)
		}
	}
}
