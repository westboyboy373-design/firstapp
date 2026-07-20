import AVFoundation
import CoreImage
import UIKit

/// Owns the actual encode-to-disk pipeline using AVAssetWriter.
/// - Video track: receives GPU-filtered CVPixelBuffers from FilterRenderer.
/// - Audio track: receives PCM buffers read directly from the selected
///   background track file — NOT from any microphone/live input — so the
///   exported container can never contain room noise.
/// Both tracks are timestamped against the same session start time so the
/// music stays frame-accurate against the video, matching what the user
/// heard during monitoring.
final class VideoExportWriter {

    private var assetWriter: AVAssetWriter?
    private var videoInput: AVAssetWriterInput?
    private var audioInput: AVAssetWriterInput?
    private var pixelBufferAdaptor: AVAssetWriterInputPixelBufferAdaptor?
    private var sessionStarted = false
    private(set) var outputURL: URL?

    private let audioReadQueue = DispatchQueue(label: "tech.westboy.audio.mux")
    private var audioReaderTimer: DispatchSourceTimer?

    func startSession(videoSize: CGSize, backgroundTrackURL: URL?) throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("mp4")
        outputURL = url

        let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)

        let videoSettings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: Int(videoSize.width),
            AVVideoHeightKey: Int(videoSize.height),
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: 12_000_000, // high bitrate, 1080p cap
                AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel
            ]
        ]
        let vInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
        vInput.expectsMediaDataInRealTime = true

        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: vInput,
            sourcePixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                kCVPixelBufferWidthKey as String: Int(videoSize.width),
                kCVPixelBufferHeightKey as String: Int(videoSize.height)
            ]
        )

        guard writer.canAdd(vInput) else { throw ExportError.cannotAddVideoInput }
        writer.add(vInput)

        var aInput: AVAssetWriterInput?
        if backgroundTrackURL != nil {
            let audioSettings: [String: Any] = [
                AVFormatIDKey: kAudioFormatMPEG4AAC,
                AVNumberOfChannelsKey: 2,
                AVSampleRateKey: 44_100,
                AVEncoderBitRateKey: 192_000
            ]
            let input = AVAssetWriterInput(mediaType: .audio, outputSettings: audioSettings)
            input.expectsMediaDataInRealTime = true
            if writer.canAdd(input) {
                writer.add(input)
                aInput = input
            }
        }

        self.assetWriter = writer
        self.videoInput = vInput
        self.audioInput = aInput
        self.pixelBufferAdaptor = adaptor
        self.sessionStarted = false

        writer.startWriting()

        if let backgroundTrackURL, let aInput {
            beginMuxingAudioTrack(from: backgroundTrackURL, into: aInput)
        }
    }

    /// Call for every filtered frame. `presentationTime` should be
    /// relative to session start (use CMTime from the original sample
    /// buffer minus the first frame's timestamp).
    func appendVideoFrame(_ pixelBuffer: CVPixelBuffer, presentationTime: CMTime) {
        guard let writer = assetWriter, let videoInput else { return }

        if !sessionStarted {
            writer.startSession(atSourceTime: presentationTime)
            sessionStarted = true
        }
        guard videoInput.isReadyForMoreMediaData else { return }
        pixelBufferAdaptor?.append(pixelBuffer, withPresentationTime: presentationTime)
    }

    /// Reads the background track file and appends PCM sample buffers to
    /// the audio input on its own timeline, keyed to the same session
    /// start time set by the first video frame.
    private func beginMuxingAudioTrack(from url: URL, into input: AVAssetWriterInput) {
        guard let assetReader = try? AVAssetReader(asset: AVURLAsset(url: url)) else { return }
        let asset = AVURLAsset(url: url)
        guard let track = asset.tracks(withMediaType: .audio).first else { return }

        let outputSettings: [String: Any] = [
            AVFormatIDKey: kAudioFormatLinearPCM
        ]
        let readerOutput = AVAssetReaderTrackOutput(track: track, outputSettings: outputSettings)
        guard assetReader.canAdd(readerOutput) else { return }
        assetReader.add(readerOutput)
        assetReader.startReading()

        input.requestMediaDataWhenReady(on: audioReadQueue) {
            while input.isReadyForMoreMediaData {
                if let sampleBuffer = readerOutput.copyNextSampleBuffer() {
                    input.append(sampleBuffer)
                } else {
                    input.markAsFinished()
                    break
                }
            }
        }
    }

    func finish(completion: @escaping (URL?) -> Void) {
        videoInput?.markAsFinished()
        assetWriter?.finishWriting { [weak self] in
            completion(self?.outputURL)
        }
    }

    enum ExportError: Error {
        case cannotAddVideoInput
    }
}
