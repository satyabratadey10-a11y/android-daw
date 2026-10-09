#include "SpscRingBuffer.h"

namespace daw {
namespace audio {

// Explicit template instantiations for common DAW audio data types
template class SpscRingBuffer<float>;
template class SpscRingBuffer<uint8_t>;
template class SpscRingBuffer<int16_t>;
template class SpscRingBuffer<int32_t>;
template class SpscRingBuffer<double>;

} // namespace audio
} // namespace daw
