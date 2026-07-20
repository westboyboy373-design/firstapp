import AVFoundation
import CoreImage
import UIKit
import Combine

enum AspectRatioPreset: String, CaseIterable, Identifiable {
    case nineBySixteen = "9:16"
    case oneByOne = "1:1"
    case sixteenByNine = "16:9"
    var id: String { rawValue }
}

enum CameraFacing {
    case front, back
}

/// Owns the AVCaptureSession, camera device configuration, and the raw
/// video output pipeline. Deliberately does NOT attach an audio input —
/// microphone capture is never enabled (see spec: zero room noise in
/// exported video). Background-track audio is handled separately by
/// AudioManager and muxed in by VideoExportWriter.
final class CameraManager: NSObject, ObservableObject {

    // MARK: Published UI state
    @Published var isRecording = false
    @Published var recordingDuration: TimeInterval = 0
    @Published var currentFacing: CameraFacing = .back
    @Published var zoomFactor: CGFloat = 1.0
    @Published var aspectRatio: AspectRatioPreset = .nineBySixteen
    @Published var isFlashOn = false
    @Published var isGridVisible = false

    // MARK: AVFoundation
    let session = AVCaptureSession()
    private var videoDeviceInput: AVCaptureDeviceInput?
    private let videoDataOutput = AVCaptureVideoDataOutput()
    private let sessionQueue = DispatchQueue(label: "tech.westboy.camera.session")
    private var durationTimer: Timer?
    private var recordingStartTime: Date?

    // Consumers (filter renderer, exporter) subscribe to raw sample buffers here.
    weak var sampleBufferDelegate: AVCaptureVideoDataOutputSampleBufferDelegate?

    // Target capture spec, capped per product requirement.
    private let maxResolution = CGSize(width: 1920, height: 1080)
    private let preferredFrameRate: Double = 60
    private let fallbackFrameRate: Double = 30

    override init() {
        super.init()
        NotificationCenter.default.addObserver(
            self, selector: #selector(handleAppBackgrounded),
            name: .westboyAppDidEnterBackground, object: nil
        )
    }

    // MARK: Setup

    func requestAccessAndConfigure(completion: @escaping (Bool) -> Void) {
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            guard let self else { return }
            guard granted else { completion(false); return }
            self.sessionQueue.async {
                self.configureSession()
                completion(true)
            }
        }
    }

    /// Builds the capture session: selects the highest-quality format that
    /// still satisfies our 1080p cap, locks focus/exposure where the
    /// hardware allows it, and picks 60fps if supported, else 30fps.
    private func configureSession() {
        session.beginConfiguration()
        session.sessionPreset = .hd1920x1080 // hard cap at 1080p per spec

        attachVideoInput(facing: currentFacing)

        if session.canAddOutput(videoDataOutput) {
            videoDataOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
            ]
            videoDataOutput.alwaysDiscardsLateVideoFrames = true
            videoDataOutput.setSampleBufferDelegate(sampleBufferDelegate, queue: sessionQueue)
            session.addOutput(videoDataOutput)
        }

        // Deliberately no AVCaptureDeviceInput(.audio) is ever added.

        session.commitConfiguration()
        session.startRunning()
    }

    private func attachVideoInput(facing: CameraFacing) {
        if let existing = videoDeviceInput {
            session.removeInput(existing)
        }
        let position: AVCaptureDevice.Position = facing == .back ? .back : .front
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else { return }

        session.addInput(input)
        videoDeviceInput = input
        configureDevice(device)
    }

    /// Locks exposure/focus/frame-rate to the best values the hardware
    /// exposes. Every `lockedDurationConfiguration` block MUST be paired
    /// with `unlockForConfiguration()` or the device stays locked forever.
    private func configureDevice(_ device: AVCaptureDevice) {
        do {
            try device.lockForConfiguration()

            // Frame rate: prefer 60fps, fall back to 30fps.
            let target = device.formats.first { format in
                let dims = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
                let fitsResolution = CGFloat(dims.width) <= maxResolution.width
                    && CGFloat(dims.height) <= maxResolution.height
                let supports60 = format.videoSupportedFrameRateRanges.contains {
                    $0.maxFrameRate >= preferredFrameRate
                }
                return fitsResolution && supports60
            }
            if let target {
                device.activeFormat = target
                device.activeVideoMinFrameDuration = CMTime(value: 1, timescale: Int32(preferredFrameRate))
                device.activeVideoMaxFrameDuration = CMTime(value: 1, timescale: Int32(preferredFrameRate))
            } else {
                device.activeVideoMinFrameDuration = CMTime(value: 1, timescale: Int32(fallbackFrameRate))
                device.activeVideoMaxFrameDuration = CMTime(value: 1, timescale: Int32(fallbackFrameRate))
            }

            // Focus / exposure: use continuous auto initially; UI can call
            // lockFocusAndExposure() once the user taps-to-lock.
            if device.isFocusModeSupported(.continuousAutoFocus) {
                device.focusMode = .continuousAutoFocus
            }
            if device.isExposureModeSupported(.continuousAutoExposure) {
                device.exposureMode = .continuousAutoExposure
            }
            if device.isLowLightBoostSupported {
                device.automaticallyEnablesLowLightBoostWhenAvailable = true
            }

            device.unlockForConfiguration()
        } catch {
            print("⚠️ Could not lock device for configuration: \(error)")
        }
    }

    // MARK: Public controls

    func switchCamera() {
        sessionQueue.async {
            self.session.beginConfiguration()
            let newFacing: CameraFacing = self.currentFacing == .back ? .front : .back
            self.attachVideoInput(facing: newFacing)
            self.session.commitConfiguration()
            DispatchQueue.main.async { self.currentFacing = newFacing }
        }
    }

    func setZoom(_ factor: CGFloat) {
        guard let device = videoDeviceInput?.device else { return }
        let clamped = min(max(factor, 1.0), device.activeFormat.videoMaxZoomFactor)
        do {
            try device.lockForConfiguration()
            device.videoZoomFactor = clamped
            device.unlockForConfiguration()
            DispatchQueue.main.async { self.zoomFactor = clamped }
        } catch {
            print("⚠️ Zoom failed: \(error)")
        }
    }

    func toggleTorch() {
        guard let device = videoDeviceInput?.device, device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            device.torchMode = device.torchMode == .on ? .off : .on
            device.unlockForConfiguration()
            DispatchQueue.main.async { self.isFlashOn = device.torchMode == .on }
        } catch {
            print("⚠️ Torch toggle failed: \(error)")
        }
    }

    func lockFocusAndExposure(atPoint point: CGPoint) {
        guard let device = videoDeviceInput?.device else { return }
        do {
            try device.lockForConfiguration()
            if device.isFocusPointOfInterestSupported {
                device.focusPointOfInterest = point
                device.focusMode = .autoFocus
            }
            if device.isExposurePointOfInterestSupported {
                device.exposurePointOfInterest = point
                device.exposureMode = .autoExpose
            }
            device.unlockForConfiguration()
        } catch {
            print("⚠️ Focus/exposure lock failed: \(error)")
        }
    }

    // MARK: Recording lifecycle (duration tracking only — actual frame
    // writing is owned by VideoExportWriter, which this view model drives).

    func beginRecordingTimer() {
        recordingStartTime = Date()
        isRecording = true
        durationTimer = Timer.scheduledTimer(withTimeInterval: 1.0 / 30.0, repeats: true) { [weak self] _ in
            guard let self, let start = self.recordingStartTime else { return }
            self.recordingDuration = Date().timeIntervalSince(start)
        }
    }

    func endRecordingTimer() {
        isRecording = false
        durationTimer?.invalidate()
        durationTimer = nil
        recordingDuration = 0
        recordingStartTime = nil
    }

    // MARK: Lifecycle teardown

    @objc private func handleAppBackgrounded() {
        sessionQueue.async {
            if self.session.isRunning {
                self.session.stopRunning()
            }
        }
    }

    func resumeIfNeeded() {
        sessionQueue.async {
            if !self.session.isRunning {
                self.session.startRunning()
            }
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }
}
