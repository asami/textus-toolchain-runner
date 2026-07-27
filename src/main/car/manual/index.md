# Textus Toolchain Runner Reference Manual

Textus Toolchain Runner is the CNCF component boundary for commands provided by
the Textus toolchain Docker image. It owns Docker invocation, controlled
workspaces, and structured operation results; calling components do not run the
image directly.

## Operations

- `Toolchain.convertSvgToPdf` converts one SVG into one PDF.
- `Toolchain.convertSvgPagesToPdf` converts ordered SVG pages into one PDF.
- `Toolchain.renderWebPage` renders an HTTP(S) page through Playwright.

The generated CML help is the authoritative input and output schema:
`src/main/cozy/textus-toolchain-runner.cml`.

## Conversion Failures

Conversion results always include `valid`. A failed result can include
`failureKind`:

- `invalid-input` means the SVG path or operation input must be corrected.
- `execution-failed` means the configured Docker/toolchain command failed or
  did not create the requested artifact.

`failureKind` is optional for compatibility with results from earlier component
releases. Consumers must retain a safe generic failure path when it is absent.
The provider-neutral `ToolchainRunner` SPI preserves it in
`ToolchainArtifactResponse.metadata.failureKind`.

## Configuration

The default image is `ghcr.io/asami/textus-toolchain:latest`. Override it with
`textus.toolchain-runner.docker.image` or the relevant operation input when an
assembly provides a reviewed alternative image.

## Related Guide

The component user guide documents CLI examples, direct Docker use, and the
browser-rendering workflow at `/man/textus-toolchain-runner/user-guide.md`.
