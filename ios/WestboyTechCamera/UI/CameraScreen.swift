import SwiftUI
import AVFoundation

struct CameraScreen: View {
    @StateObject private var cameraManager = CameraManager()
    @StateObject private var audioManager = AudioManager()
    @State private var filterEngine = FilterEngine()
    @State private var exportWriter = VideoExportWriter()
    @State private var frameProcessor: FrameProcessor?

    @State private var currentFilter: FilterType = .none
    @State private var previewImage: CGImage?
    @State private var pinchBaseZoom: CGFloat = 1.0
    @State private var showTrackPicker = false
    @State private var showSettings = false
    @State private var maxTrackedDuration: Double = 60

    var body: some View {
        ZStack {
            CameraPreviewView(session: cameraManager.session)
                .ignoresSafeArea()
                .gesture(pinchGesture)
                .gesture(swipeGesture)
                .onTapGesture { location in
                    cameraManager.lockFocusAndExposure(atPoint: location)
                }

            watermark
            topBar
            VStack {
                Spacer()
                if audioManager.isPlaying {
                    WaveformView(amplitudes: audioManager.currentAmplitudes)
                        .padding(.bottom, 8)
                }
                FilterDockView(selected: currentFilter) { filter in
                    currentFilter = filter
                    frameProcessor?.currentFilter = filter
                }
                .padding(.bottom, 10)

                RecordButton(
                    isRecording: cameraManager.isRecording,
                    progress: (cameraManager.recordingDuration.truncatingRemainder(dividingBy: maxTrackedDuration)) / maxTrackedDuration
                ) {
                    toggleRecording()
                }
                .padding(.bottom, 24)
            }
        }
        .background(Theme.background)
        .onAppear(perform: setup)
        .sheet(isPresented: $showTrackPicker) {
            TrackPickerView(audioManager: audioManager)
        }
        .sheet(isPresented: $showSettings) {
            SettingsView()
        }
    }

    // MARK: Setup

    private func setup() {
        let processor = FrameProcessor(filterEngine: filterEngine, exportWriter: exportWriter)
        frameProcessor = processor
        cameraManager.sampleBufferDelegate = processor
        cameraManager.requestAccessAndConfigure { granted in
            guard granted else { return }
        }
    }

    // MARK: Recording

    private func toggleRecording() {
        if cameraManager.isRecording {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private func startRecording() {
        do {
            try exportWriter.startSession(
                videoSize: CGSize(width: 1080, height: 1920),
                backgroundTrackURL: audioManager.selectedTrack?.fileURL
            )
        } catch {
            print("⚠️ Failed to start export session: \(error)")
            return
        }
        frameProcessor?.resetSessionTiming()
        frameProcessor?.isRecording = true
        audioManager.startMonitoring()
        cameraManager.beginRecordingTimer()
    }

    private func stopRecording() {
        frameProcessor?.isRecording = false
        audioManager.stopMonitoring()
        cameraManager.endRecordingTimer()
        exportWriter.finish { url in
            guard let url else { return }
            DispatchQueue.main.async {
                // Hand off to a save/share sheet (e.g. UIActivityViewController
                // or PHPhotoLibrary.shared().performChanges) — wired up at the
                // app-integration layer.
                print("✅ Export complete: \(url)")
            }
        }
    }

    // MARK: Gestures

    private var pinchGesture: some Gesture {
        MagnificationGesture()
            .onChanged { value in
                let newZoom = pinchBaseZoom * value
                cameraManager.setZoom(newZoom)
            }
            .onEnded { _ in
                pinchBaseZoom = cameraManager.zoomFactor
            }
    }

    private var swipeGesture: some Gesture {
        DragGesture(minimumDistance: 40)
            .onEnded { value in
                guard abs(value.translation.width) > abs(value.translation.height) else { return }
                let filters = FilterType.allCases
                guard let index = filters.firstIndex(of: currentFilter) else { return }
                withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                    if value.translation.width < 0, index < filters.count - 1 {
                        currentFilter = filters[index + 1]
                    } else if value.translation.width > 0, index > 0 {
                        currentFilter = filters[index - 1]
                    }
                }
                frameProcessor?.currentFilter = currentFilter
            }
    }

    // MARK: HUD

    private var watermark: some View {
        VStack {
            Spacer()
            Text(Branding.watermarkText)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(.white.opacity(0.45))
                .padding(.bottom, 6)
        }
    }

    private var topBar: some View {
        VStack {
            HStack {
                Button { cameraManager.toggleTorch() } label: {
                    Image(systemName: cameraManager.isFlashOn ? "bolt.fill" : "bolt.slash")
                }
                Button { cameraManager.isGridVisible.toggle() } label: {
                    Image(systemName: "grid")
                }
                Button { showTrackPicker = true } label: {
                    Image(systemName: "music.note")
                }
                Spacer()
                Menu {
                    ForEach(AspectRatioPreset.allCases) { preset in
                        Button(preset.rawValue) { cameraManager.aspectRatio = preset }
                    }
                } label: {
                    Text(cameraManager.aspectRatio.rawValue)
                }
                Button { cameraManager.switchCamera() } label: {
                    Image(systemName: "arrow.triangle.2.circlepath.camera")
                }
                Button { showSettings = true } label: {
                    Image(systemName: "gearshape")
                }
            }
            .font(.system(size: 18))
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .glassPanel(cornerRadius: 18)
            .padding(.horizontal, 12)
            .padding(.top, 8)
            Spacer()
        }
    }
}
