#ifndef ANDROID_DAW_ENGINE_API_H
#define ANDROID_DAW_ENGINE_API_H

#include <cstdint>
#include <cstddef>

#ifdef __cplusplus
extern "C" {
#endif

// =============================================================================
// Engine Constants & Telemetry Definitions
// =============================================================================

#define DAW_MAX_TRACKS 64
#define DAW_PPQ 960

typedef struct {
    int64_t playback_frame;
    float master_peak_left;
    float master_peak_right;
    float master_rms_left;
    float master_rms_right;
    float cpu_load_percent;
    int32_t active_tracks;
    float track_peak_left[DAW_MAX_TRACKS];
    float track_peak_right[DAW_MAX_TRACKS];
    float track_rms_left[DAW_MAX_TRACKS];
    float track_rms_right[DAW_MAX_TRACKS];
} DawTelemetryPayload;

// =============================================================================
// Engine Lifecycle & Audio Device Interface
// =============================================================================

int daw_engine_init(int sample_rate, int frames_per_burst);
void daw_engine_release(void);
int daw_engine_is_initialized(void);

// =============================================================================
// Transport Controls
// =============================================================================

void daw_engine_play(void);
void daw_engine_pause(void);
void daw_engine_stop(void);
void daw_engine_seek(int64_t frame_position);
void daw_engine_set_tempo(double bpm);
void daw_engine_set_loop(int enabled, int64_t start_frame, int64_t end_frame);
int64_t daw_engine_get_position(void);
double daw_engine_get_tempo(void);
int daw_engine_is_playing(void);

// =============================================================================
// Track Management & Mixing
// =============================================================================

int daw_engine_add_track(const char* name);
void daw_engine_remove_track(int track_id);
void daw_engine_set_track_gain(int track_id, float linear_gain);
void daw_engine_set_track_pan(int track_id, float pan_position);
void daw_engine_set_track_mute(int track_id, int muted);
void daw_engine_set_track_solo(int track_id, int soloed);
void daw_engine_set_track_armed(int track_id, int armed);

// =============================================================================
// Audio Clips & Timeline Placement
// =============================================================================

int daw_engine_load_clip(int track_id, int clip_id, const char* audio_file_path, int64_t start_offset_frames);
int daw_engine_move_clip(int track_id, int clip_id, int64_t new_start_frame);
int daw_engine_remove_clip(int track_id, int clip_id);

// =============================================================================
// MIDI & Notes Interface
// =============================================================================

void daw_engine_note_on(int track_id, int pitch, int velocity);
void daw_engine_note_off(int track_id, int pitch);
void daw_engine_all_notes_off(int track_id);

// =============================================================================
// Plugins & DSP Parameters
// =============================================================================

int daw_engine_add_plugin(int track_id, int plugin_type);
void daw_engine_remove_plugin(int track_id, int plugin_index);
void daw_engine_set_param(int track_id, int plugin_index, int param_id, float value);

// Equalizer & Delay Direct Controls
void daw_engine_set_track_eq_band(int track_id, int band_index, int filter_type, float freq_hz, float gain_db, float q_factor);
void daw_engine_set_track_delay(int track_id, float delay_ms, float feedback, float wet_dry);
void daw_engine_set_master_limiter(float threshold_db, float ceiling_db, float release_ms);
void daw_engine_set_synth_params(int track_id, float cutoff_hz, float resonance, float drive);
void daw_engine_set_synth_adsr(int track_id, float attack_sec, float decay_sec, float sustain_level, float release_sec);

// =============================================================================
// Telemetry & Level Metering
// =============================================================================

void daw_engine_get_telemetry(DawTelemetryPayload* out_payload);
float daw_engine_get_cpu_usage(void);

// =============================================================================
// Recording & Offline Rendering
// =============================================================================

int daw_engine_start_recording(int track_id, const char* destination_wav_path);
void daw_engine_stop_recording(int track_id);
int daw_engine_render_offline(const char* output_wav_path, int bit_depth, int64_t total_frames);

#ifdef __cplusplus
}
#endif

#endif // ANDROID_DAW_ENGINE_API_H
