# textus-toolchain-runner

`textus-toolchain-runner` exposes the existing Cozy toolchain Docker image as a
Textus/CNCF component.

The component is intentionally broader than SVG/PDF conversion. It is the
execution boundary for artifact-related tools that live in the Cozy toolchain
image.

Default Docker image:

```text
ghcr.io/asami/cozy-toolchain:latest
```

The image can be overridden with operation parameters or config:

```yaml
textus:
  toolchain-runner:
    docker:
      image: ghcr.io/asami/cozy-toolchain:latest
```

## Operations

### convertSvgToPdf

Converts one SVG into one PDF through the Cozy toolchain image.

```sh
textus-toolchain-runner convert-svg-to-pdf --svg input.svg --out output.pdf
```

### convertSvgPagesToPdf

Converts multiple SVG pages into one multi-page PDF.

```sh
textus-toolchain-runner convert-svg-pages-to-pdf --svgFiles main.svg,detail.svg --out print.pdf
```

Implementation note: the component runs the configured Docker command with the
Cozy toolchain image and invokes the image-level `svg-pages-to-pdf` subcommand.
That subcommand is part of the toolchain image contract; the Textus component
owns Docker invocation, workspace mounting, and operation results.

## Sanpomap Integration

Sanpomap can generate SVG pages and delegate PDF conversion to this component.
This keeps Sanpomap focused on map/route rendering while keeping Python,
Poppler, browser, and conversion tools inside the Cozy toolchain boundary.

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
