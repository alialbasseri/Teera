import Foundation
import Combine

public enum ConnectionState: Equatable {
    case disconnected
    case connecting
    case connected
    case error(String)
}

public final class WebSocketClient: NSObject, ObservableObject {
    public static let shared = WebSocketClient()

    @Published public var state: ConnectionState = .disconnected
    @Published public var currentRoom: RoomInfo?
    @Published public var playerView: PlayerView?
    @Published public var playerId: String = ""
    @Published public var playerName: String = "لاعب"
    @Published public var errorMessage: String?

    private var webSocketTask: URLSessionWebSocketTask?
    private var urlSession: URLSession!
    private var pingTimer: Timer?
    private let serverURL = URL(string: "wss://teera-xfvr.onrender.com/ws")!

    /// This app's own build number, sent with "platform": "ios" so the server never
    /// mistakes it for an Android build (whose numbers it uses to offer APK updates).
    public static let iosBuild = 1

    /// Set when the player leaves on purpose; otherwise a dropped line is retried.
    private var wantsConnection = false
    private var reconnectAttempts = 0
    private var reconnectWork: DispatchWorkItem?

    override private init() {
        super.init()
        self.urlSession = URLSession(configuration: .default, delegate: self, delegateQueue: .main)
        loadPlayerProfile()
    }

    private func loadPlayerProfile() {
        if let savedId = UserDefaults.standard.string(forKey: "teera_player_id"), !savedId.isEmpty {
            self.playerId = savedId
        } else {
            let newId = UUID().uuidString.lowercased()
            self.playerId = newId
            UserDefaults.standard.set(newId, forKey: "teera_player_id")
        }

        if let savedName = UserDefaults.standard.string(forKey: "teera_player_name"), !savedName.isEmpty {
            self.playerName = savedName
        }
    }

    public func savePlayerName(_ name: String) {
        self.playerName = name
        UserDefaults.standard.set(name, forKey: "teera_player_name")
        if state == .connected {
            sendHello()
        }
    }

    public func connect() {
        wantsConnection = true
        guard webSocketTask == nil else { return }
        state = .connecting

        // The free server sleeps; waking it can take most of a minute.
        var request = URLRequest(url: serverURL)
        request.timeoutInterval = 90
        let task = urlSession.webSocketTask(with: request)
        webSocketTask = task
        task.resume()

        receiveMessage(on: task)
        startPingTimer()
    }

    public func disconnect() {
        wantsConnection = false
        reconnectWork?.cancel()
        tearDown()
        state = .disconnected
    }

    private func tearDown() {
        pingTimer?.invalidate()
        pingTimer = nil
        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil
    }

    /// A line that dropped (or a server still waking) is tried again, waiting a little
    /// longer each time; back at the table, the room is rejoined into the same chair.
    private func lineDropped(_ reason: String) {
        tearDown()
        guard wantsConnection else { state = .disconnected; return }
        state = .connecting
        reconnectAttempts += 1
        let wait = min(15.0, pow(2.0, Double(min(reconnectAttempts, 4))))
        reconnectWork?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.connect() }
        reconnectWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + wait, execute: work)
        print("Teera: connection lost (\(reason)); retrying in \(wait)s")
    }

    private func startPingTimer() {
        pingTimer?.invalidate()
        pingTimer = Timer.scheduledTimer(withTimeInterval: 25.0, repeats: true) { [weak self] _ in
            self?.send(rawJSON: "{\"t\":\"ping\"}")
        }
    }

    private func sendHello() {
        let msg: [String: Any] = [
            "t": "hello",
            "name": playerName,
            "playerId": playerId,
            "version": 2,
            "avatar": "p:0",
            "appVersion": WebSocketClient.iosBuild,
            "platform": "ios"
        ]
        send(jsonDict: msg)
    }

    // MARK: - API Calls

    public func createRoom(rules: Rules = .default) {
        let rulesDict: [String: Any] = [
            "target": rules.target,
            "turnSeconds": rules.turnSeconds,
            "alertVoteSeconds": rules.alertVoteSeconds,
            "alertsPerPenalty": rules.alertsPerPenalty,
            "alertPenalty": rules.alertPenalty,
            "xBonus": rules.xBonus,
            "games": rules.games
        ]
        send(jsonDict: ["t": "createRoom", "rules": rulesDict])
    }

    public func joinRoom(code: String) {
        send(jsonDict: ["t": "joinRoom", "code": code.uppercased(), "rejoin": false])
    }

    public func leaveRoom() {
        send(jsonDict: ["t": "leaveRoom"])
        currentRoom = nil
        playerView = nil
    }

    public func takeSeat(_ seat: Int) {
        send(jsonDict: ["t": "takeSeat", "seat": seat])
    }

    public func setReady(_ isReady: Bool) {
        send(jsonDict: ["t": "ready", "ready": isReady])
    }

    public func setBot(seat: Int, enabled: Bool, difficulty: String = "NORMAL") {
        send(jsonDict: ["t": "setBot", "seat": seat, "enabled": enabled, "difficulty": difficulty])
    }

    public func startMatch() {
        send(jsonDict: ["t": "startMatch"])
    }

    public func makeMove(_ move: Move) {
        var moveDict: [String: Any] = ["t": move.type.rawValue]
        if let d = move.domino {
            moveDict["domino"] = ["low": d.low, "high": d.high]
        }
        if let a = move.arm {
            moveDict["arm"] = a.rawValue
        }
        send(jsonDict: ["t": "move", "move": moveDict])
    }

    public func nextGame() {
        send(jsonDict: ["t": "nextGame"])
    }

    /// After the match: the whole table goes back to the room's lobby for another.
    public func backToLobby() {
        send(jsonDict: ["t": "backToLobby"])
    }

    public func sendEmote(_ emote: Emote) {
        send(jsonDict: ["t": "emote", "emote": emote.rawValue])
    }

    public func sendChat(_ text: String) {
        send(jsonDict: ["t": "chat", "text": text])
    }

    // MARK: - Networking

    private func send(jsonDict: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: jsonDict),
              let string = String(data: data, encoding: .utf8) else {
            return
        }
        send(rawJSON: string)
    }

    private func send(rawJSON: String) {
        webSocketTask?.send(.string(rawJSON)) { error in
            if let error = error {
                print("WebSocket send error: \(error.localizedDescription)")
            }
        }
    }

    private func receiveMessage(on task: URLSessionWebSocketTask) {
        task.receive { [weak self] result in
            guard let self = self else { return }

            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleIncoming(text: text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleIncoming(text: text)
                    }
                @unknown default:
                    break
                }
                self.receiveMessage(on: task)

            case .failure(let error):
                DispatchQueue.main.async {
                    // Only the line in use: an old one closing late changes nothing.
                    guard self.webSocketTask === task else { return }
                    self.lineDropped(error.localizedDescription)
                }
            }
        }
    }

    /// The server wraps what it sends: {"t":"room","room":{...}}, {"t":"view","view":{...}}.
    private struct RoomEnvelope: Decodable { let room: RoomInfo }
    private struct ViewEnvelope: Decodable { let view: PlayerView }

    private func handleIncoming(text: String) {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["t"] as? String else {
            return
        }

        DispatchQueue.main.async {
            switch type {
            case "welcome":
                if let pid = json["playerId"] as? String {
                    self.playerId = pid
                    UserDefaults.standard.set(pid, forKey: "teera_player_id")
                }
                self.state = .connected
                self.reconnectAttempts = 0
                // Back after a dropped line: sit down in the same chair again.
                if let room = self.currentRoom {
                    self.send(jsonDict: ["t": "joinRoom", "code": room.code, "rejoin": true])
                }

            case "room":
                do {
                    let room = try JSONDecoder().decode(RoomEnvelope.self, from: data).room
                    self.currentRoom = room
                    // Back in the lobby (a new room, or after a match): no table to show.
                    if !room.started { self.playerView = nil }
                } catch {
                    print("Teera: could not read a room: \(error)")
                }

            case "view":
                do {
                    self.playerView = try JSONDecoder().decode(ViewEnvelope.self, from: data).view
                } catch {
                    print("Teera: could not read a view: \(error)")
                }

            case "error":
                let codeStr = json["code"] as? String
                let message = json["message"] as? String
                // The server says important things in plain words under INTERNAL
                // (barred, too old, a notice): show its words, not a generic error.
                if let codeStr = codeStr, let code = ErrorCode(rawValue: codeStr), code != .INTERNAL {
                    self.errorMessage = code.localizedDescriptionArabic
                } else if let message = message, !message.isEmpty {
                    self.errorMessage = message
                } else {
                    self.errorMessage = ErrorCode.INTERNAL.localizedDescriptionArabic
                }
                if codeStr == ErrorCode.KICKED.rawValue {
                    self.currentRoom = nil
                    self.playerView = nil
                }

            case "emote":
                if let emoteStr = json["emote"] as? String,
                   let emote = Emote(rawValue: emoteStr) {
                    SoundManager.shared.playEmote(emote)
                }

            default:
                break
            }
        }
    }
}

extension WebSocketClient: URLSessionWebSocketDelegate {
    public func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        DispatchQueue.main.async {
            self.state = .connected
            self.sendHello()
        }
    }

    public func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        DispatchQueue.main.async {
            guard self.webSocketTask === webSocketTask else { return }
            self.lineDropped("closed \(closeCode.rawValue)")
        }
    }
}
