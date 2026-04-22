// StatusHelper.swift
// macOS native helper that shows a countdown (or any short text) in the menu bar
// via NSStatusItem. Activation policy is .accessory so the helper has no Dock
// icon and no menu bar of its own — it only contributes a single status item.
//
// Build:
//   swiftc -O -o status-helper StatusHelper.swift -framework AppKit
//
// Protocol (newline-delimited JSON):
//   Commands (stdin):
//     {"cmd":"set","text":"2:45"}
//     {"cmd":"quit"}
//   Responses (stdout):
//     {"type":"ready"}

import AppKit
import Foundation

func writeLine(_ line: String) {
    FileHandle.standardOutput.write(Data((line + "\n").utf8))
}

class StatusItemManager {
    let item: NSStatusItem

    init() {
        item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        item.button?.title = "—:—"
        // Monospaced digits keep the width stable as the countdown ticks.
        item.button?.font = NSFont.monospacedDigitSystemFont(ofSize: 13, weight: .regular)
    }

    func setText(_ text: String) {
        item.button?.title = text
    }
}

class StdinReader {
    let manager: StatusItemManager

    init(manager: StatusItemManager) {
        self.manager = manager
    }

    func startReading() {
        let thread = Thread { self.readLoop() }
        thread.name = "StdinReader"
        thread.start()
    }

    private func readLoop() {
        while let line = readLine(strippingNewline: true) {
            guard !line.isEmpty else { continue }
            guard let data = line.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let cmd = json["cmd"] as? String else {
                continue
            }

            switch cmd {
            case "set":
                if let text = json["text"] as? String {
                    DispatchQueue.main.async { self.manager.setText(text) }
                }
            case "quit":
                DispatchQueue.main.async { NSApplication.shared.terminate(nil) }
                return
            default:
                break
            }
        }
        // stdin closed — parent died or disconnected; exit.
        DispatchQueue.main.async { NSApplication.shared.terminate(nil) }
    }
}

class AppDelegate: NSObject, NSApplicationDelegate {
    var manager: StatusItemManager!
    var reader: StdinReader!

    func applicationDidFinishLaunching(_ notification: Notification) {
        manager = StatusItemManager()
        reader = StdinReader(manager: manager)
        reader.startReading()
        writeLine("{\"type\":\"ready\"}")
    }
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)
let delegate = AppDelegate()
app.delegate = delegate
app.run()
