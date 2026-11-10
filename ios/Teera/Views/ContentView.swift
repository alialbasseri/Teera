import SwiftUI

public struct ContentView: View {
    @ObservedObject var client = WebSocketClient.shared

    public var body: some View {
        ZStack {
            if client.playerView != nil && client.currentRoom?.started == true {
                TableScreen()
                    .transition(.opacity)
            } else if client.currentRoom != nil {
                LobbyView()
                    .transition(.slide)
            } else {
                MenuView()
                    .transition(.opacity)
            }

            // Error Toast
            if let error = client.errorMessage {
                VStack {
                    Text(error)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Felt.parchment)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Felt.danger)
                        .cornerRadius(10)
                        .shadow(radius: 6)
                        .padding(.top, 40)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                .onAppear {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                        client.errorMessage = nil
                    }
                }
            }
        }
        .animation(.easeInOut(duration: 0.25), value: client.currentRoom != nil)
        .animation(.easeInOut(duration: 0.25), value: client.playerView != nil)
        .onAppear {
            client.connect()
        }
    }
}
