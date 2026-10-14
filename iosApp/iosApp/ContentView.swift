import ComposeApp
import SwiftUI
import UIKit

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // 안전 영역/키보드 인셋은 Compose(WindowInsets.safeDrawing)가 처리한다.
        ComposeView().ignoresSafeArea()
    }
}
