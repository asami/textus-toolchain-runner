# textus-toolchain-runner

`textus-toolchain-runner` owns the Textus toolchain Docker image and exposes it
as a Textus/CNCF component.

The component is intentionally broader than SVG/PDF conversion. It is the
execution boundary for artifact-related tools that live in the Textus toolchain
image. Cozy uses this image as a consumer; Docker image sources live in this
repository under `docker/textus-toolchain`.

## Manual

The canonical component developer manual is packaged in the CAR and served
when the component is running:

```text
/man/{component}
/man/{component}/user-guide.html
/man/{component}/user-guide.md
```

Generated component help/specification is served separately:

```text
/help/{component}
```

The CML source at `src/main/cozy/textus-toolchain-runner.cml` is the operation
contract for component users, generated APIs, CAR packaging, and runtime
integration. This keeps the toolchain behavior, public operation names, inputs,
and outputs in the same repository as the implementation and Docker image
assets. The component developer manual explains how to use the component and how
to use the Docker image directly.

Default Docker image:

```text
ghcr.io/asami/textus-toolchain:latest
```

The image can be overridden with operation parameters or config:

```yaml
textus:
  toolchain-runner:
    docker:
      image: ghcr.io/asami/textus-toolchain:latest
```

## Operations

### convertSvgToPdf

Converts one SVG into one PDF through the Textus toolchain image.

```sh
textus-toolchain-runner convert-svg-to-pdf --svg input.svg --out output.pdf
```

### convertSvgPagesToPdf

Converts multiple SVG pages into one multi-page PDF.

```sh
textus-toolchain-runner convert-svg-pages-to-pdf --svgFiles main.svg,detail.svg --out print.pdf
```

Implementation note: the component runs the configured Docker command with the
Textus toolchain image and invokes the image-level `svg-pages-to-pdf` subcommand.
That subcommand is part of the toolchain image contract; the Textus component
owns Docker invocation, workspace mounting, and operation results.

### Failure Results

Failed conversion responses set `valid: false` and may include `failureKind`:

- `invalid-input`: the supplied SVG path or operation input is invalid. Callers
  should correct the request rather than retrying the runtime.
- `execution-failed`: the Docker/toolchain command failed or produced no output.
  Callers may report a service failure and retry after the runtime is repaired.

The field is optional so consumers can continue to handle artifacts produced by
earlier Toolchain Runner releases that did not classify failures.

The provider-neutral `ToolchainRunner` SPI preserves this value in
`ToolchainArtifactResponse.metadata.failureKind`.

### renderWebPage

Renders one HTTP(S) page with Playwright/Chromium and returns structured page
metadata and rendered HTML. CNCF components should call the provider-neutral
`ToolchainRunner.renderWebPage` SPI; the Textus Scraper `RenderPage` operation
is the standard HTML normalization boundary.

An optional User-Agent is propagated literally as browser context
configuration. A caller may additionally select the explicit
`reviewed_site_compatibility` browser profile when a reviewed source requires
coherent Chromium client hints and reduced automation disclosure. The standard
profile does not change browser process flags.

```text
TextusScraperApi.RenderPage
  -> ToolchainRunner.renderWebPage
  -> textus-toolchain-runner
  -> Playwright/Chromium
```

The component passes request and response JSON through mounted files. URLs and
rendered HTML are not placed in Docker command arguments or stdout.

## Sanpomap Integration

Sanpomap can generate SVG pages and delegate PDF conversion to this component.
This keeps Sanpomap focused on map/route rendering while keeping Python,
Poppler, browser, and conversion tools inside the Textus toolchain boundary.

```text
Sanpomap renderScenarioPrintPdf
  -> generate Route DSL / Presentation DSL / SVG pages
  -> ToolchainRunner convertSvgPagesToPdf
  -> print-ready PDF
```

## Development

```sh
sbt test
scripts/check-render-web-page-browser-profile.sh
scripts/update-runtime-classpath.sh
```
