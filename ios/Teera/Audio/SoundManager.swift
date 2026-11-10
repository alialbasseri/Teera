import Foundation
import AVFoundation
import UIKit

public final class SoundManager: ObservableObject {
    public static let shared = SoundManager()

    @Published public var soundEnabled: Bool = true
    private var players: [String: AVAudioPlayer] = [:]
    private let impactLight = UIImpactFeedbackGenerator(style: .light)
    private let impactMedium = UIImpactFeedbackGenerator(style: .medium)
    private let impactHeavy = UIImpactFeedbackGenerator(style: .heavy)

    private init() {
        impactLight.prepare()
        impactMedium.prepare()
        impactHeavy.prepare()
    }

    public func playClack() {
        guard soundEnabled else { return }
        impactMedium.impactOccurred()
        let index = Int.random(in: 0...2)
        playSound(named: "sfx_clack_\(index)")
    }

    public func playKnock() {
        guard soundEnabled else { return }
        impactHeavy.impactOccurred()
        playSound(named: "sfx_knock")
    }

    public func playSlide() {
        guard soundEnabled else { return }
        impactLight.impactOccurred()
        playSound(named: "sfx_slide")
    }

    public func playTeera() {
        guard soundEnabled else { return }
        impactHeavy.impactOccurred()
        playSound(named: "sfx_teera")
    }

    public func playX() {
        guard soundEnabled else { return }
        impactHeavy.impactOccurred()
        playSound(named: "sfx_x")
    }

    public func playWon() {
        guard soundEnabled else { return }
        playSound(named: "sfx_match_won")
    }

    public func playLost() {
        guard soundEnabled else { return }
        playSound(named: "sfx_match_lost")
    }

    public func playEmote(_ emote: Emote) {
        guard soundEnabled else { return }
        switch emote {
        case .GULP: playSound(named: "emote_gulp")
        case .NO_WAY: playSound(named: "emote_no_way")
        case .DRUMROLL: playSound(named: "emote_drumroll")
        case .SAD: playSound(named: "emote_sad")
        case .SLAP: playSound(named: "emote_slap")
        default: playSound(named: "emote_clink")
        }
    }

    private func playSound(named name: String) {
        guard let url = Bundle.main.url(forResource: name, withExtension: "ogg") ??
                        Bundle.main.url(forResource: name, withExtension: "wav") ??
                        Bundle.main.url(forResource: name, withExtension: "m4a") else {
            return
        }

        do {
            let player = try AVAudioPlayer(contentsOf: url)
            player.prepareToPlay()
            player.play()
            players[name] = player
        } catch {
            // Audio playback error handled gracefully
        }
    }
}
