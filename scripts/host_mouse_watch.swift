#!/usr/bin/env swift
/**
 Host mouse click stream for map calib.
 Prints one JSON object per line on left-button edges:
   {"edge":"down"|"up","x":123,"y":456,"buttons":1}
 Coordinates are CG/top-left global (same space as CGWindow bounds).
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

var lastButtons = -1
while true {
    autoreleasepool {
        let buttons = Int(NSEvent.pressedMouseButtons)
        if buttons != lastButtons {
            let (x, y) = cgMouse()
            let leftNow = (buttons & 1) != 0
            let leftWas = (lastButtons & 1) != 0
            if leftNow && !leftWas {
                print("{\"edge\":\"down\",\"x\":\(Int(x)),\"y\":\(Int(y)),\"buttons\":\(buttons)}")
                fflush(stdout)
            } else if !leftNow && leftWas {
                print("{\"edge\":\"up\",\"x\":\(Int(x)),\"y\":\(Int(y)),\"buttons\":\(buttons)}")
                fflush(stdout)
            }
            lastButtons = buttons
        }
        Thread.sleep(forTimeInterval: 0.012)
    }
}
