#ifndef ANDROID_DAW_MASTER_LIMITER_H
#define ANDROID_DAW_MASTER_LIMITER_H

#include <vector>
#include <cstddef>
#include <cmath>
#include <algorithm>

namespace daw {
namespace dsp {

/**
 * Master Lookahead Limiter with Soft Saturation Knee.
 *
 * Implements:
 * 1. 5ms lookahead circular delay buffer.
 * 2. Peak detector & dual attack/release envelope follower ballistics.
 * 3. Dynamic gain computer enforcing output threshold & ceiling.
 * 4. Cubic soft-knee saturation ensuring zero ceiling overshoot.
 */
class MasterLimiter {
public:
    explicit MasterLimiter(float sample_rate = 48000.0f, float lookahead_ms = 5.0f);
    ~MasterLimiter() = default;

    void setSampleRate(float sample_rate) noexcept;

    // Threshold in dBFS (e.g. -1.0 dBFS)
    void setThresholdDb(float threshold_db) noexcept;
    void set_threshold_db(float threshold_db) noexcept { setThresholdDb(threshold_db); }
    [[nodiscard]] float getThresholdDb() const noexcept { return threshold_db_; }

    // Ceiling in dBFS (e.g. -0.1 dBFS)
    void setCeilingDb(float ceiling_db) noexcept;
    void set_ceiling_db(float ceiling_db) noexcept { setCeilingDb(ceiling_db); }
    [[nodiscard]] float getCeilingDb() const noexcept { return ceiling_db_; }

    // Release time in milliseconds (e.g. 10.0 ms to 200.0 ms)
    void setReleaseMs(float release_ms) noexcept;
    void set_release_ms(float release_ms) noexcept { setReleaseMs(release_ms); }
    [[nodiscard]] float getReleaseMs() const noexcept { return release_ms_; }

    // Attack time in milliseconds (e.g. 0.5 ms to 5.0 ms)
    void setAttackMs(float attack_ms) noexcept;

    void setEnabled(bool enabled) noexcept { enabled_ = enabled; }
    [[nodiscard]] bool isEnabled() const noexcept { return enabled_; }

    // In-place processing on planar stereo buffers
    void processStereo(float* left, float* right, size_t num_frames) noexcept;

    // Mono out-of-place processing (for unit test compatibility)
    void process(const float* input, float* output, size_t num_frames) noexcept;

    // Planar stereo out-of-place processing
    void process(const float* in_left, const float* in_right,
                 float* out_left, float* out_right,
                 size_t num_frames) noexcept;

    // Current gain reduction in dB (for telemetry meters)
    [[nodiscard]] float getCurrentGainReductionDb() const noexcept;

    void reset() noexcept;

private:
    void updateBallistics() noexcept;
    static inline float softClipCubic(float x, float ceiling) noexcept;

    float sample_rate_{48000.0f};
    float lookahead_ms_{5.0f};
    size_t lookahead_samples_{240};

    float threshold_db_{-1.0f};
    float ceiling_db_{-0.1f};
    float threshold_linear_{0.89125f};
    float ceiling_linear_{0.98855f};

    float attack_ms_{1.0f};
    float release_ms_{80.0f};
    float attack_coeff_{0.979f};
    float release_coeff_{0.9997f};

    float envelope_{0.0f};
    float current_gain_{1.0f};
    bool enabled_{true};

    // Lookahead circular delay buffers
    std::vector<float> delay_buf_l_;
    std::vector<float> delay_buf_r_;
    size_t delay_buf_capacity_{512};
    size_t delay_buf_mask_{511};
    size_t delay_write_pos_{0};
};

} // namespace dsp
} // namespace daw

#endif // ANDROID_DAW_MASTER_LIMITER_H
