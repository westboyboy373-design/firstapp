import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

enum FilterType: String, CaseIterable, Identifiable {
    case none = "Original"
    case classicBW = "Classic B&W"
    case cyberpunk = "Cyberpunk"
    case vintage90s = "Vintage 90s"
    case cinematic = "Cinematic"

    var id: String { rawValue }
}

/// Applies each look via a small Core Image filter graph. The SAME graph
/// is used for the live preview (FilterPreviewView) and for export
/// (FrameProcessor -> VideoExportWriter), so what the user sees while
/// shooting matches the final file exactly.
final class FilterEngine {
    private let context: CIContext

    init(mtlDevice: MTLDevice? = MTLCreateSystemDefaultDevice()) {
        if let mtlDevice {
            context = CIContext(mtlDevice: mtlDevice)
        } else {
            context = CIContext(options: nil) // software fallback
        }
    }

    func apply(_ filter: FilterType, to image: CIImage) -> CIImage {
        switch filter {
        case .none:
            return image
        case .classicBW:
            return classicBW(image)
        case .cyberpunk:
            return cyberpunk(image)
        case .vintage90s:
            return vintage90s(image)
        case .cinematic:
            return cinematic(image)
        }
    }

    /// Renders a CIImage back into a CVPixelBuffer for the video writer.
    func render(_ image: CIImage, into pixelBuffer: CVPixelBuffer) {
        context.render(image, to: pixelBuffer)
    }

    // MARK: - Looks

    private func classicBW(_ image: CIImage) -> CIImage {
        let mono = CIFilter.photoEffectNoir()
        mono.inputImage = image
        let contrast = CIFilter.colorControls()
        contrast.inputImage = mono.outputImage
        contrast.contrast = 1.35
        contrast.brightness = -0.02
        return contrast.outputImage ?? image
    }

    private func cyberpunk(_ image: CIImage) -> CIImage {
        let matrix = CIFilter.colorMatrix()
        matrix.inputImage = image
        // Push blues/magentas, pull down green channel slightly for a neon feel.
        matrix.rVector = CGVector(dx: 1.15, dy: 0)
        matrix.gVector = CGVector(dx: 0, dy: 0.85)
        matrix.bVector = CGVector(dx: 0, dy: 1.3)
        let vibrance = CIFilter.vibrance()
        vibrance.inputImage = matrix.outputImage
        vibrance.amount = 0.6
        return vibrance.outputImage ?? image
    }

    private func vintage90s(_ image: CIImage) -> CIImage {
        let sepia = CIFilter.sepiaTone()
        sepia.inputImage = image
        sepia.intensity = 0.25
        let grain = CIFilter.randomGenerator()
        guard let grainImage = grain.outputImage?.cropped(to: image.extent) else {
            return sepia.outputImage ?? image
        }
        let grainMono = CIFilter.colorControls()
        grainMono.inputImage = grainImage
        grainMono.saturation = 0
        let composite = CIFilter.softLightBlendMode()
        composite.inputImage = grainMono.outputImage
        composite.backgroundImage = sepia.outputImage
        return composite.outputImage ?? image
    }

    private func cinematic(_ image: CIImage) -> CIImage {
        let desat = CIFilter.colorControls()
        desat.inputImage = image
        desat.saturation = 0.6
        desat.contrast = 1.1
        // Letterbox is applied as a separate compositing step in
        // FilterPreviewView/FrameProcessor, layered as black bars over
        // the top/bottom of the frame — kept optional per spec.
        return desat.outputImage ?? image
    }
}
