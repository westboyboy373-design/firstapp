import AVFoundation
import CoreImage
import MetalKit

/// Sits between CameraManager's raw sample buffer output and both
/// (a) the live filtered preview and (b) VideoExportWriter. Ensures the
/// exact same filtered CIImage is what gets shown AND what gets encoded.
final class FrameProcessor: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {

    private let filterEngine: FilterEngine
    private let exportWriter: VideoExportWriter
    var currentFilter: FilterType = .none
    var isRecording = false
    var letterboxEnabled = false

    /// Latest filtered image, published for the MTKView preview layer to draw.
    var onFilteredFrame: ((CIImage) -> Void)?

    private var firstSampleTime: CMTime?
    private var pixelBufferPool: CVPixelBufferPool?

    init(filterEngine: FilterEngine, exportWriter: VideoExportWriter) {
        self.filterEngine = filterEngine
        self.exportWriter = exportWriter
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        var image = CIImage(cvPixelBuffer: pixelBuffer)
        image = filterEngine.apply(currentFilter, to: image)

        if currentFilter == .cinematic && letterboxEnabled {
            image = applyLetterbox(to: image)
        }

        onFilteredFrame?(image)

        guard isRecording else { return }

        let presentationTime = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        if firstSampleTime == nil { firstSampleTime = presentationTime }

        if pixelBufferPool == nil {
            pixelBufferPool = makePixelBufferPool(width: Int(image.extent.width), height: Int(image.extent.height))
        }
        guard let pool = pixelBufferPool else { return }

        var outputBuffer: CVPixelBuffer?
        CVPixelBufferPoolCreatePixelBuffer(nil, pool, &outputBuffer)
        guard let outputBuffer else { return }

        filterEngine.render(image, into: outputBuffer)
        exportWriter.appendVideoFrame(outputBuffer, presentationTime: presentationTime)
    }

    func resetSessionTiming() {
        firstSampleTime = nil
    }

    private func applyLetterbox(to image: CIImage) -> CIImage {
        let barHeight = image.extent.height * 0.10
        let topBar = CIImage(color: .black).cropped(
            to: CGRect(x: image.extent.minX, y: image.extent.maxY - barHeight,
                       width: image.extent.width, height: barHeight)
        )
        let bottomBar = CIImage(color: .black).cropped(
            to: CGRect(x: image.extent.minX, y: image.extent.minY,
                       width: image.extent.width, height: barHeight)
        )
        return topBar.composited(over: bottomBar.composited(over: image))
    }

    private func makePixelBufferPool(width: Int, height: Int) -> CVPixelBufferPool? {
        let attributes: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: width,
            kCVPixelBufferHeightKey as String: height,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ]
        var pool: CVPixelBufferPool?
        CVPixelBufferPoolCreate(nil, nil, attributes as CFDictionary, &pool)
        return pool
    }
}
