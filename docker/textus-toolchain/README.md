# Textus Toolchain Docker Image

`ghcr.io/asami/textus-toolchain:latest` is the standard Textus toolchain Docker
image for Cozy BoK, SmartDox PDF, and video workflows.

This image can be used in two ways:

- through the `textus-toolchain-runner` CNCF component, which provides the
  component specification, operation results, CallTree/observability, configured
  Docker command selection, and controlled workspace handling;
- directly as a Docker image, for standalone CI jobs, local diagnosis, or tools
  that only need the packaged command-line environment.

The component contract is managed beside the implementation in
`src/main/cozy/textus-toolchain-runner.cml`. This Docker manual is for image
builders and maintainers. The component developer manual is packaged in the CAR
at `/man/{component}` and can be fetched as Markdown at
`/man/{component}/user-guide.md`.

The canonical registry is GitHub Container Registry under the `asami` account:

```text
ghcr.io/asami/textus-toolchain
```

Release tags use `YYYY.MM.DD`. If a same-day rebuild is required, use
`YYYY.MM.DD-N`. The `latest` tag must point only to the latest validated release
image.

Development and pre-release validation can use the current Cozy SNAPSHOT
version as an image tag. Do not push SNAPSHOT tags as validated releases.

```bash
docker build \
  -t ghcr.io/asami/textus-toolchain:0.2.1-SNAPSHOT \
  docker/textus-toolchain

docker run --rm ghcr.io/asami/textus-toolchain:0.2.1-SNAPSHOT textus-toolchain check all
```

Build:

```bash
docker build \
  -t ghcr.io/asami/textus-toolchain:2026.07.06 \
  -t ghcr.io/asami/textus-toolchain:latest \
  docker/textus-toolchain
```

Validate the installed dependency sets:

```bash
docker run --rm ghcr.io/asami/textus-toolchain:2026.07.06 textus-toolchain check all
docker run --rm ghcr.io/asami/textus-toolchain:latest textus-toolchain check all
docker run --rm ghcr.io/asami/textus-toolchain:latest textus-toolchain check kroki
docker run --rm ghcr.io/asami/textus-toolchain:latest textus-toolchain check bok
docker run --rm ghcr.io/asami/textus-toolchain:latest textus-toolchain check pdf
docker run --rm ghcr.io/asami/textus-toolchain:latest textus-toolchain check video
docker run --rm ghcr.io/asami/textus-toolchain:latest textus-toolchain check tts
```

The video check requires the Remotion CLI and verifies that `remotion`,
`@remotion/cli`, and `@remotion/renderer` use one compatible version.
Docker build arguments pin the Antora, Asciidoctor Kroki, Playwright, and
Remotion npm dependency families so rebuilding the image does not silently
change its runtime contract.

Release:

```bash
docker login ghcr.io
docker push ghcr.io/asami/textus-toolchain:2026.07.06
docker push ghcr.io/asami/textus-toolchain:latest
```

Verify the pushed image:

```bash
docker pull ghcr.io/asami/textus-toolchain:2026.07.06
docker run --rm ghcr.io/asami/textus-toolchain:2026.07.06 textus-toolchain check all
docker image inspect ghcr.io/asami/textus-toolchain:2026.07.06
```

Current release:

```text
tag: ghcr.io/asami/textus-toolchain:2026.07.06
latest: ghcr.io/asami/textus-toolchain:latest
digest: sha256:7d91af5ae740e2cf818da07a2c497e4d3f6768b5445b811a35e530f978de5183
```

The image is based on the SmartDox `smartdox-pdf` dependency image line and keeps its Kroki command/server behavior:

```bash
docker run --rm -p 9609:8000 ghcr.io/asami/textus-toolchain:latest kroki-server
```

When Cozy runs Antora through this image, the wrapper starts the bundled Kroki
server inside the container and serves it on `localhost:9609`, matching the
SmartDox Antora playbook compatibility setting. Direct SmartDox usage can still
use the existing `simplemodeling/smartdox-pdf:latest` Docker image or an
explicit Kroki server.

The wrapper passes through unknown commands, so tools remain directly runnable:

```bash
docker run --rm ghcr.io/asami/textus-toolchain:latest antora --version
docker run --rm ghcr.io/asami/textus-toolchain:latest ffmpeg -version
docker run --rm ghcr.io/asami/textus-toolchain:latest python3 -c 'import PIL; print(PIL.__version__)'
```

For direct image users, the wrapper exposes the image-level `svg-pages-to-pdf`
command used by Textus toolchain integrations:

```bash
docker run --rm -v "$PWD:/work" -w /work \
  ghcr.io/asami/textus-toolchain:latest \
  svg-pages-to-pdf --out print.pdf page-001.svg page-002.svg
```

The image also exposes the low-level Playwright command used by
`textus-toolchain-runner`. CNCF callers should prefer ToolchainRunner SPI or
Textus Scraper. Direct use is intended for deterministic CI and diagnosis:

```json
{
  "url": "https://example.org/application",
  "waitUntil": "networkidle",
  "waitForSelector": "#application",
  "timeoutSeconds": 30,
  "userAgent": "MuseumBrowser/1.0",
  "browserProfile": "standard"
}
```

```bash
docker run --rm -v "$PWD:/work" -w /work \
  ghcr.io/asami/textus-toolchain:latest \
  render-web-page --request request.json --response response.json
```

The response file contains requested/final URL, status, title, rendered HTML,
timestamp, engine, and browser. Diagnostics go to stderr. The command emits no
rendered HTML on stdout.

The request may include `userAgent`, which the standard profile applies
literally. A reviewed source may explicitly select
`browserProfile: reviewed_site_compatibility`; only that profile aligns Chromium
client hints and reduces automation disclosure without exposing raw headers or
launch arguments to the application component.

VOICEVOX Engine is not included. Cozy checks and uses VOICEVOX as an external HTTP service.

Portable English narration is provided by Piper `1.4.2`. The image contains
the `en_US-ljspeech-medium` model (public-domain source dataset) and
`en_US-joe-medium` model (CC0 source dataset). Runtime and model downloads are
pinned and SHA-256 verified during the image build. The same checks run through
`textus-toolchain check tts`; provenance and license references are available
at `/opt/textus/models/piper/manifest.json`.

Generate canonical 24 kHz mono PCM16 WAV without runtime network access:

```bash
printf '%s\n' 'Portable narration.' > narration.txt
docker run --rm --network=none -v "$PWD:/work" -w /work \
  ghcr.io/asami/textus-toolchain:0.2.1-SNAPSHOT \
  textus-toolchain piper-synthesize \
  --model en_US-ljspeech-medium \
  --input narration.txt \
  --output narration.wav
```

`scripts/check-piper-tts.sh` builds only the snapshot tag and performs this
offline smoke for both bundled models. Do not publish that tag as a validated
release image.

The v1 image includes whisper.cpp and the standard model at:

```text
/opt/textus/models/ggml-base.bin
```
