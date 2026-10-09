#ifndef ANDROID_DAW_SPSC_RING_BUFFER_H
#define ANDROID_DAW_SPSC_RING_BUFFER_H

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>
#include <algorithm>
#include <new>

namespace daw {
namespace audio {

#if defined(__cpp_lib_hardware_interference_size) && !defined(__ANDROID__)
using std::hardware_destructive_interference_size;
#else
constexpr size_t hardware_destructive_interference_size = 64;
#endif

/**
 * Lock-Free Single-Producer Single-Consumer (SPSC) Ring Buffer.
 *
 * Guarantees:
 * 1. Zero heap allocations during push/pop/read/write.
 * 2. Zero locks or system calls on the real-time audio thread.
 * 3. False-sharing elimination via cache-line alignment (alignas(64)).
 * 4. Power-of-two capacity with single-cycle bitwise masking.
 * 5. Sequential consistency avoided; explicit acquire-release semantics.
 */
template <typename T>
class SpscRingBuffer {
public:
    explicit SpscRingBuffer(size_t minimum_capacity = 1024) {
        // Enforce power-of-two capacity
        capacity_ = 1;
        while (capacity_ < minimum_capacity) {
            capacity_ <<= 1;
        }
        mask_ = capacity_ - 1;
        buffer_.resize(capacity_);
        write_index_.store(0, std::memory_order_relaxed);
        read_index_.store(0, std::memory_order_relaxed);
    }

    ~SpscRingBuffer() = default;

    // Non-copyable, non-movable for deterministic RT memory safety
    SpscRingBuffer(const SpscRingBuffer&) = delete;
    SpscRingBuffer& operator=(const SpscRingBuffer&) = delete;
    SpscRingBuffer(SpscRingBuffer&&) = delete;
    SpscRingBuffer& operator=(SpscRingBuffer&&) = delete;

    [[nodiscard]] size_t capacity() const noexcept {
        return capacity_;
    }

    [[nodiscard]] size_t read_available() const noexcept {
        const size_t write_idx = write_index_.load(std::memory_order_acquire);
        const size_t read_idx = read_index_.load(std::memory_order_relaxed);
        return write_idx - read_idx;
    }

    [[nodiscard]] size_t write_available() const noexcept {
        const size_t write_idx = write_index_.load(std::memory_order_relaxed);
        const size_t read_idx = read_index_.load(std::memory_order_acquire);
        return capacity_ - (write_idx - read_idx);
    }

    // Push single element (Producer only)
    bool push(const T& item) noexcept {
        const size_t write_idx = write_index_.load(std::memory_order_relaxed);
        const size_t read_idx = read_index_.load(std::memory_order_acquire);

        if (capacity_ - (write_idx - read_idx) < 1) {
            return false; // Buffer full
        }

        buffer_[write_idx & mask_] = item;
        write_index_.store(write_idx + 1, std::memory_order_release);
        return true;
    }

    // Pop single element (Consumer only)
    bool pop(T& item) noexcept {
        const size_t read_idx = read_index_.load(std::memory_order_relaxed);
        const size_t write_idx = write_index_.load(std::memory_order_acquire);

        if (write_idx == read_idx) {
            return false; // Buffer empty
        }

        item = buffer_[read_idx & mask_];
        read_index_.store(read_idx + 1, std::memory_order_release);
        return true;
    }

    // Batch write (Producer only): Returns count of elements written
    size_t write(const T* source, size_t count) noexcept {
        if (!source || count == 0) return 0;

        const size_t write_idx = write_index_.load(std::memory_order_relaxed);
        const size_t read_idx = read_index_.load(std::memory_order_acquire);
        const size_t available = capacity_ - (write_idx - read_idx);
        const size_t to_write = std::min(count, available);

        if (to_write == 0) return 0;

        const size_t start_offset = write_idx & mask_;
        const size_t first_chunk = std::min(to_write, capacity_ - start_offset);

        std::copy_n(source, first_chunk, buffer_.data() + start_offset);
        if (to_write > first_chunk) {
            std::copy_n(source + first_chunk, to_write - first_chunk, buffer_.data());
        }

        write_index_.store(write_idx + to_write, std::memory_order_release);
        return to_write;
    }

    // Batch read (Consumer only): Returns count of elements read
    size_t read(T* destination, size_t count) noexcept {
        if (!destination || count == 0) return 0;

        const size_t read_idx = read_index_.load(std::memory_order_relaxed);
        const size_t write_idx = write_index_.load(std::memory_order_acquire);
        const size_t available = write_idx - read_idx;
        const size_t to_read = std::min(count, available);

        if (to_read == 0) return 0;

        const size_t start_offset = read_idx & mask_;
        const size_t first_chunk = std::min(to_read, capacity_ - start_offset);

        std::copy_n(buffer_.data() + start_offset, first_chunk, destination);
        if (to_read > first_chunk) {
            std::copy_n(buffer_.data(), to_read - first_chunk, destination + first_chunk);
        }

        read_index_.store(read_idx + to_read, std::memory_order_release);
        return to_read;
    }

    void reset() noexcept {
        write_index_.store(0, std::memory_order_relaxed);
        read_index_.store(0, std::memory_order_relaxed);
    }

private:
    size_t capacity_{0};
    size_t mask_{0};
    std::vector<T> buffer_;

    // Explicit cache-line separation prevents false sharing between producer & consumer cores
    alignas(hardware_destructive_interference_size) std::atomic<size_t> write_index_{0};
    alignas(hardware_destructive_interference_size) std::atomic<size_t> read_index_{0};
};

} // namespace audio
} // namespace daw

#endif // ANDROID_DAW_SPSC_RING_BUFFER_H
