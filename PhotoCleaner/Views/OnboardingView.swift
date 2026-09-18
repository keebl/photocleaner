import SwiftUI

/// Korte introductie bij de eerste start: wat de app doet, dat opruimen veilig is,
/// en welke bronnen er zijn. Wordt één keer getoond.
struct OnboardingView: View {
    var onDone: () -> Void

    @State private var page = 0

    private struct Page: Identifiable {
        let id = UUID()
        let icon: String
        let tint: Color
        let title: String
        let text: String
    }

    private let pages: [Page] = [
        Page(icon: "sparkles", tint: .accentColor,
             title: "Ruim je foto's op",
             text: "Beoordeel foto's per dag ('op deze dag'), willekeurig, of vind dubbelen. Veeg naar rechts om te behouden, naar links om weg te gooien."),
        Page(icon: "clock.arrow.circlepath", tint: .green,
             title: "Veilig opruimen",
             text: "Weggegooide foto's blijven 30 dagen in de prullenbak — één tik om iets ongedaan te maken. Foto's die je behoudt, komen niet meer langs."),
        Page(icon: "externaldrive.badge.plus", tint: .blue,
             title: "iPhone of NAS",
             text: "Werk met je iPhone-bibliotheek, of koppel je NAS rechtstreeks in de app via SMB. Je wachtwoord blijft veilig op je toestel.")
    ]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                if page < pages.count - 1 {
                    Button("Overslaan") { onDone() }
                        .foregroundStyle(.secondary)
                }
            }
            .padding()

            TabView(selection: $page) {
                ForEach(Array(pages.enumerated()), id: \.element.id) { index, item in
                    pageView(item).tag(index)
                }
            }
            .tabViewStyle(.page)
            .indexViewStyle(.page(backgroundDisplayMode: .always))

            Button {
                if page < pages.count - 1 {
                    withAnimation { page += 1 }
                } else {
                    onDone()
                }
            } label: {
                Text(page < pages.count - 1 ? "Volgende" : "Aan de slag")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
    }

    private func pageView(_ item: Page) -> some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: item.icon)
                .font(.system(size: 84))
                .foregroundStyle(item.tint)
            Text(item.title)
                .font(.title).bold()
                .multilineTextAlignment(.center)
            Text(item.text)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Spacer()
            Spacer()
        }
    }
}
