#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SMOKE_ROOT="${PROJECT_ROOT}/target/cncf.d/piper-tts"
IMAGE="${TEXTUS_TOOLCHAIN_TTS_IMAGE:-textus-toolchain:0.2.1-SNAPSHOT}"

mkdir -p "$SMOKE_ROOT"
rm -f "$SMOKE_ROOT"/*.txt "$SMOKE_ROOT"/*.wav
printf '%s\n' 'The Textus toolchain provides portable offline narration.' > "$SMOKE_ROOT/ljspeech.txt"
printf '%s\n' 'Cozy can select a second bundled voice without network access.' > "$SMOKE_ROOT/joe.txt"

docker build -t "$IMAGE" "$PROJECT_ROOT/docker/textus-toolchain"
docker run --rm --network=none "$IMAGE" textus-toolchain check tts
docker run --rm --network=none -v "$SMOKE_ROOT:/work" -w /work "$IMAGE" \
  textus-toolchain piper-synthesize \
  --model en_US-ljspeech-medium \
  --input ljspeech.txt \
  --output ljspeech.wav
docker run --rm --network=none -v "$SMOKE_ROOT:/work" -w /work "$IMAGE" \
  textus-toolchain piper-synthesize \
  --model en_US-joe-medium \
  --input joe.txt \
  --output joe.wav

for output in ljspeech.wav joe.wav; do
  test -s "$SMOKE_ROOT/$output"
  format="$(docker run --rm --network=none -v "$SMOKE_ROOT:/work" -w /work "$IMAGE" \
    ffprobe -v error -select_streams a:0 \
    -show_entries stream=codec_name,sample_rate,channels,bits_per_sample \
    -of csv=p=0 "$output")"
  test "$format" = "pcm_s16le,24000,1,16"
done

printf 'Piper TTS offline Docker smoke passed: %s\n' "$IMAGE"
