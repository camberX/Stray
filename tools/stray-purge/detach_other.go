//go:build !windows

package main

func detach() bool {
	return false
}
