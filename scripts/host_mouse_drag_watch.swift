#!/usr/bin/env swift
/**
 Host mouse drag stream for input mirroring.

 Emits one JSON object per line on:
   - left button down: {"edge":"down","x":...,"y":...,"ts":...,"buttons":...}
   - while pressed:    {"edge":"move","x":...,"y":...,"ts":...,"buttons":...}
   - left button up:   {"edge":"up","x":...,"y":...,"ts":...,"buttons":...,
                         "down_x":...,"down_y":...,"dur_ms":...}

 Coordinates are CG/top-left global (same space as CGWindow bounds),
 so host->device mapping can reuse existing letterbox logic.
 */
import Cocoa
import Foundation

func cgMouse() -> (Double, Double) {
    let cocoa = NSEvent.mouseLocation
    // Convert bottom-left Cocoa → top-left CG (primary display height)
    let h = NSScreen.screens.map { $0.frame.origin.y + $0.frame.size.height }.max() ?? 0
    // Standard: primary screen height for y flip when origin is primary bottom-left
    let primaryH = NSScreen.screens.first(where: { $0.frame.origin == .zero })?.frame.height
        ?? NSScreen.main?.frame.height
        ?? h
    return (cocoa.x, primaryH - cocoa.y)
}

func nowMs() -> Int64 {
    // Monotonic-ish for gesture duration, good enough for replay timing.
    return Int64((Date().timeIntervalSince1970 * 1000.0).rounded())
}

var lastButtons = 0
var leftPressed = false
var downX: Int = 0
var downY: Int = 0
var downTs: Int64 = 0
var lastMoveTs: Int64 = 0

// Throttle move events so stdout doesn't explode while dragging.
let minMoveGapMs: Int64 = 18

while true {
    autoreleasepool {
        let buttons = Int(NSEvent.pressedMouseButtons)
        let leftNow = (buttons & 1) != 0
        let leftWas = (lastButtons & 1) != 0

        let (x, y) = cgMouse()
        let xi = Int(x.rounded())
        let yi = Int(y.rounded())
        let ts = nowMs()

        if leftNow && !leftWas {
            leftPressed = true
            downX = xi
            downY = yi
            downTs = ts
            print("{\"edge\":\"down\",\"x\":\(xi),\"y\":\(yi),\"ts\":\(ts),\"buttons\":\(buttons)}")
            fflush(stdout)
        } else if leftNow && leftPressed {
            // While pressed: emit move events (throttled)
            if ts - lastMoveTs >= minMoveGapMs {
                print("{\"edge\":\"move\",\"x\":\(xi),\"y\":\(yi),\"ts\":\(ts),\"buttons\":\(buttons)}")
                fflush(stdout)
                lastMoveTs = ts
            }
        } else if !leftNow && leftWas {
            // Lift: report final end coords + gesture duration + down coords.
            leftPressed = false
            let dur = max(0, ts - downTs)
            print(
                "{\"edge\":\"up\",\"x\":\(xi),\"y\":\(yi),\"ts\":\(ts),\"buttons\":\(buttons),\"down_x\":\(downX),\"down_y\":\(downY),\"dur_ms\":\(dur)}"
            )
            fflush(stdout)
        }

        lastButtons = buttons
        Thread.sleep(forTimeInterval: 0.012)
    }
}

