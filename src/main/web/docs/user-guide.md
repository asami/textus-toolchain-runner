# Textus Toolchain Runner Component Developer Guide

Textus Toolchain Runner owns the Textus toolchain Docker image and exposes
artifact conversion tools as CNCF component operations and as a ToolchainRunner
SPI provider.

## Document Routes

When the CAR is running, CNCF serves this guide as a component-packaged manual,
separate from generated CML/component help.

```text
/man/{component}
/man/{component}/user-guide.html
/man/{component}/user-guide.md
```

The generated component specification is served separately:

```text
/help/{component}
```

If a packaged PDF is added as `docs/user-guide.pdf`, CNCF serves it at:

```text
/man/{component}/user-guide.pdf
```

Command-line clients can fetch the Markdown or PDF directly:

```sh
curl -fsS http://localhost:8080/man/textus-toolchain-runner/user-guide.md
curl -fsS -o user-guide.pdf http://localhost:8080/man/textus-toolchain-runner/user-guide.pdf
```

## Source of Truth

This guide is the human-authored component developer manual for the running
component. The CML source at `src/main/cozy/textus-toolchain-runner.cml` remains
the operation contract for generated APIs, CAR packaging, runtime integration,
and component users.

## Default Image

Default Docker image:

```text
ghcr.io/asami/textus-toolchain:latest
```

Wrapper command:

```text
textus-toolchain
```

Main conversion command:

```text
svg-pages-to-pdf
```

The image can be overridden by operation input or runtime configuration using:

```yaml
textus:
  toolchain-runner:
    docker:
      image: ghcr.io/asami/textus-toolchain:latest
```

## Component Operations

### convertSvgToPdf

Converts one SVG file into one PDF file through the Textus toolchain image.

```sh
textus-toolchain-runner convert-svg-to-pdf --svg input.svg --out output.pdf
```

### convertSvgPagesToPdf

Converts multiple SVG pages into one multi-page PDF.

```sh
textus-toolchain-runner convert-svg-pages-to-pdf \
  --svgFiles page-001.svg,page-002.svg \
  --out print.pdf
```

## When to Use the Component

Use the component or ToolchainRunner SPI when the caller runs inside CNCF and
needs structured operation results, CallTree/observability, configured Docker
command selection, and controlled workspace handling.

Sanpomap uses this path when it generates SVG pages and delegates PDF conversion
to ToolchainRunner.

## Direct Docker Image Usage

Direct Docker use is for standalone CI jobs, local diagnosis, or tools that
only need the packaged command-line environment and do not run inside CNCF.

Pull and check the image:

```sh
docker pull ghcr.io/asami/textus-toolchain:latest
docker run --rm ghcr.io/asami/textus-toolchain:latest textus-toolchain check all
```

Convert SVG pages to PDF:

```sh
docker run --rm -v "$PWD:/work" -w /work \
  ghcr.io/asami/textus-toolchain:latest \
  svg-pages-to-pdf --out print.pdf page-001.svg page-002.svg
```

Run installed tools directly:

```sh
docker run --rm ghcr.io/asami/textus-toolchain:latest antora --version
docker run --rm ghcr.io/asami/textus-toolchain:latest ffmpeg -version
docker run --rm ghcr.io/asami/textus-toolchain:latest python3 -c 'import PIL; print(PIL.__version__)'
```

The wrapper passes through commands when no Textus-specific command matches.

## Image Builder Notes

Docker build, release tags, validation commands, and image digest notes live
with the Docker assets at `docker/textus-toolchain/README.md`. That file is for
image builders and maintainers. This user guide is the runtime user manual.
