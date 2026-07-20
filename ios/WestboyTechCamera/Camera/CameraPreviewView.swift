import SwiftUI
import AVFoundation

/// Thin UIViewRepresentable bridging AVCaptureVideoPreviewLayer into
/// SwiftUI. Filters are NOT applied here — this shows the raw feed;
/// FilterRenderer composites the live-filtered preview on top via a
/// separate MTKView layer (see FilterPreviewView).
struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewContainerView {
        let view = PreviewContainerView()
        view.videoPreviewLayer.session = session
        view.videoPreviewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewContainerView, context: Context) {}
}

final class PreviewContainerView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var videoPreviewLayer: AVCaptureVideoPreviewLayer {
        layer as! AVCaptureVideoPreviewLayer
    }
}
