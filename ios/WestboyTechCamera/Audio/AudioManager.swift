import AVFoundation
import Combine

struct BackgroundTrack: Identifiable, Hashable {
    let id = UUID()
    let title: String
    let fileURL: URL
}

/// Handles ONLY the background music track: local playback for real-time
/// monitoring while the user records. Deliberately has no connection to
/// any microphone input — that omission is what keeps the exported file
/// free of room noise. The same AVAudioFile is read a second time by
/// VideoExportWriter to bake the track into the output container, using
/// shared timing so the two stay in sync.
final class AudioManager: NSObject, ObservableObject {
    @Published var selectedTrack: BackgroundTrack?
    @Published var isPlaying = false
    /// Normalized amplitude buffer (0...1) for the waveform visualizer.
    @Published var currentAmplitudes: [Float] = Array(repeating: 0, count: 40)

    private let engine = AVAudioEngine()
    private let playerNode = AVAudioPlayerNode()
    private var audioFile: AVAudioFile?
    private let waveformBucketCount = 40

    override init() {
        super.init()
        engine.attach(playerNode)
    }

    func loadTrack(_ track: BackgroundTrack) {
        do {
            let file = try AVAudioFile(forReading: track.fileURL)
            audioFile = file
            engine.connect(playerNode, to: engine.mainMixerNode, format: file.processingFormat)
            installAmplitudeTap(format: file.processingFormat)
            selectedTrack = track
        } catch {
            print("⚠️ Failed to load track \(track.title): \(error)")
        }
    }

    /// Starts playback from the beginning. Called at the exact moment
    /// video recording starts, so playback position ≈ recording position.
    func startMonitoring() {
        guard let audioFile else { return }
        do {
            if !engine.isRunning { try engine.start() }
            playerNode.scheduleFile(audioFile, at: nil, completionHandler: nil)
            playerNode.play()
            isPlaying = true
        } catch {
            print("⚠️ Failed to start audio engine: \(error)")
        }
    }

    func stopMonitoring() {
        playerNode.stop()
        engine.stop()
        isPlaying = false
    }

    /// Installs a tap on the player node to drive the real-time waveform
    /// UI. This taps the OUTGOING signal of the track being monitored —
    /// never the microphone, since no mic node exists in this graph.
    private func installAmplitudeTap(format: AVAudioFormat) {
        playerNode.removeTap(onBus: 0)
        playerNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            guard let self, let channelData = buffer.floatChannelData else { return }
            let frameCount = Int(buffer.frameLength)
            let samples = channelData[0]

            let bucketSize = max(frameCount / self.waveformBucketCount, 1)
            var buckets = [Float](repeating: 0, count: self.waveformBucketCount)
            for bucket in 0..<self.waveformBucketCount {
                let start = bucket * bucketSize
                let end = min(start + bucketSize, frameCount)
                guard start < end else { continue }
                var sum: Float = 0
                for i in start..<end { sum += abs(samples[i]) }
                buckets[bucket] = min(sum / Float(end - start) * 4, 1.0) // gain for visibility
            }

            DispatchQueue.main.async {
                self.currentAmplitudes = buckets
            }
        }
    }

    func teardown() {
        playerNode.removeTap(onBus: 0)
        stopMonitoring()
    }
}
