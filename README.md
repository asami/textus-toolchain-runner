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

### renderWebPage

Renders one HTTP(S) page with Playwright/Chromium and returns structured page
metadata and rendered HTML. CNCF components should call the provider-neutral
`ToolchainRunner.renderWebPage` SPI; the Textus Scraper `RenderPage` operation
is the standard HTML normalization boundary.

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
scripts/update-runtime-classpath.sh
```
