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
        guard webSocketTask == nil else { return }
        state = .connecting

        webSocketTask = urlSession.webSocketTask(with: serverURL)
        webSocketTask?.resume()

        receiveMessage()
        startPingTimer()
    }

    public func disconnect() {
        pingTimer?.invalidate()
        pingTimer = nil
        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil
        state = .disconnected
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
            "appVersion": 30
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

    private func receiveMessage() {
        webSocketTask?.receive { [weak self] result in
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
                self.receiveMessage()

            case .failure(let error):
                DispatchQueue.main.async {
                    self.state = .error(error.localizedDescription)
                }
            }
        }
    }

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

            case "room":
                if let room = try? JSONDecoder().decode(RoomInfo.self, from: data) {
                    self.currentRoom = room
                }

            case "view":
                if let view = try? JSONDecoder().decode(PlayerView.self, from: data) {
                    self.playerView = view
                }

            case "error":
                if let codeStr = json["code"] as? String,
                   let code = ErrorCode(rawValue: codeStr) {
                    self.errorMessage = code.localizedDescriptionArabic
                } else if let msg = json["message"] as? String {
                    self.errorMessage = msg
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
            self.state = .disconnected
        }
    }
}
