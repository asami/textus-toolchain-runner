ARG KROKI_IMAGE=yuzutech/kroki:latest
FROM ${KROKI_IMAGE}

ARG ANTORA_VERSION=3.1.15
ARG ASCIIDOCTOR_KROKI_VERSION=0.18.1
ARG PLAYWRIGHT_VERSION=1.61.1
ARG REMOTION_VERSION=4.0.490
ARG PIPER_VERSION=1.4.2
ARG PIPER_VOICES_REF=5b44ec7bab7c5822cfec48fbd5aa99db71a823d6

ENV DEBIAN_FRONTEND=noninteractive
ENV PLAYWRIGHT_BROWSERS_PATH=/opt/playwright-browsers
ENV TEXTUS_WHISPER_MODEL=/opt/textus/models/ggml-base.bin
ENV NODE_PATH=/usr/local/lib/node_modules

USER root

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
    bash \
    build-essential \
    ca-certificates \
    cmake \
    curl \
    ffmpeg \
    fonts-ipafont-gothic \
    fonts-ipafont-mincho \
    fonts-noto-cjk \
    fonts-noto-color-emoji \
    git \
    latexmk \
    libasound2t64 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcairo2 \
    libcups2 \
    libdbus-1-3 \
    libdrm2 \
    libgbm1 \
    libglib2.0-0 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libpango-1.0-0 \
    libx11-6 \
    libxcb1 \
    libxcomposite1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxkbcommon0 \
    libxrandr2 \
    nodejs \
    npm \
    pkg-config \
    python3 \
    python3-pil \
    python3-venv \
    ruby \
    ruby-dev \
    texlive-fonts-recommended \
    texlive-lang-cjk \
    texlive-lang-japanese \
    texlive-latex-extra \
    texlive-luatex \
    unzip \
    wget \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

RUN gem install --no-document asciidoctor asciidoctor-pdf

RUN npm install -g \
    "@antora/cli@${ANTORA_VERSION}" \
    "@antora/site-generator@${ANTORA_VERSION}" \
    "asciidoctor-kroki@${ASCIIDOCTOR_KROKI_VERSION}" \
    "playwright@${PLAYWRIGHT_VERSION}" \
    "remotion@${REMOTION_VERSION}" \
    "@remotion/cli@${REMOTION_VERSION}" \
    "@remotion/renderer@${REMOTION_VERSION}" \
  && remotion versions \
  && npx playwright install chromium

RUN set -eux; \
  mkdir -p /opt/smartdox/themes; \
  printf '%s\n' \
    'extends: default' \
    'font:' \
    '  catalog:' \
    '    SmartDoxJapanese:' \
    '      normal: /usr/share/fonts/opentype/ipafont-gothic/ipag.ttf' \
    '      bold: /usr/share/fonts/opentype/ipafont-gothic/ipag.ttf' \
    '      italic: /usr/share/fonts/opentype/ipafont-gothic/ipag.ttf' \
    '      bold_italic: /usr/share/fonts/opentype/ipafont-gothic/ipag.ttf' \
    '  fallbacks:' \
    '    - SmartDoxJapanese' \
    'base:' \
    '  font_family: SmartDoxJapanese' \
    'heading:' \
    '  font_family: SmartDoxJapanese' \
    'literal:' \
    '  font_family: SmartDoxJapanese' \
    'code:' \
    '  font_family: SmartDoxJapanese' \
    > /opt/smartdox/themes/asciidoctor-pdf-ja.yml

RUN set -eux; \
  mkdir -p /tmp/smartdox-asciidoctor-warm; \
  printf '%s\n' \
    '= SmartDox Asciidoctor warm-up' \
    '' \
    'Japanese PDF: 日本語PDF' \
    > /tmp/smartdox-asciidoctor-warm/warm.adoc; \
  asciidoctor-pdf \
    -a scripts=cjk \
    -a pdf-theme=/opt/smartdox/themes/asciidoctor-pdf-ja.yml \
    -o /tmp/smartdox-asciidoctor-warm/warm.pdf \
    /tmp/smartdox-asciidoctor-warm/warm.adoc; \
  rm -rf /tmp/smartdox-asciidoctor-warm

RUN set -eux; \
  mkdir -p /tmp/smartdox-tex-warm; \
  printf '%s\n' \
    '\documentclass[uplatex,a4paper,11pt]{jsarticle}' \
    '\usepackage{geometry}' \
    '\usepackage{longtable}' \
    '\usepackage{graphicx}' \
    '\begin{document}' \
    'SmartDox TeX warm-up: 日本語PDF' \
    '\end{document}' \
    > /tmp/smartdox-tex-warm/uplatex-warm.tex; \
  uplatex -interaction=nonstopmode -halt-on-error -output-directory=/tmp/smartdox-tex-warm /tmp/smartdox-tex-warm/uplatex-warm.tex; \
  dvipdfmx -o /tmp/smartdox-tex-warm/uplatex-warm.pdf /tmp/smartdox-tex-warm/uplatex-warm.dvi; \
  luaotfload-tool --update || true; \
  printf '%s\n' \
    '\documentclass[a4paper,11pt]{ltjsarticle}' \
    '\usepackage{luatexja}' \
    '\begin{document}' \
    'SmartDox LuaTeX warm-up: 日本語PDF' \
    '\end{document}' \
    > /tmp/smartdox-tex-warm/lualatex-warm.tex; \
  lualatex -interaction=nonstopmode -halt-on-error -output-directory=/tmp/smartdox-tex-warm /tmp/smartdox-tex-warm/lualatex-warm.tex; \
  rm -rf /tmp/smartdox-tex-warm

ARG WHISPER_CPP_REF=v1.7.6
ARG WHISPER_MODEL_URL=https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin
RUN set -eux; \
  git clone --depth 1 --branch "${WHISPER_CPP_REF}" https://github.com/ggerganov/whisper.cpp.git /tmp/whisper.cpp; \
  cmake -S /tmp/whisper.cpp -B /tmp/whisper.cpp/build -DCMAKE_BUILD_TYPE=Release; \
  cmake --build /tmp/whisper.cpp/build --config Release -j2; \
  install -m 0755 /tmp/whisper.cpp/build/bin/whisper-cli /usr/local/bin/whisper-cli; \
  mkdir -p /opt/textus/models; \
  curl -fsSL "${WHISPER_MODEL_URL}" -o "${TEXTUS_WHISPER_MODEL}"; \
  rm -rf /tmp/whisper.cpp

ARG TARGETARCH
ARG PIPER_AMD64_URL=https://files.pythonhosted.org/packages/a7/58/7b7cbd7e570ac6eb8dbfc22de94d1457863b97876c1cb6a926e959423fb2/piper_tts-1.4.2-cp39-abi3-manylinux_2_17_x86_64.manylinux2014_x86_64.manylinux_2_28_x86_64.whl
ARG PIPER_AMD64_SHA256=b17184a664bd9431ce95c138f4bfb3025e1280cf26075a703dbfdcab989b8ee3
ARG PIPER_ARM64_URL=https://files.pythonhosted.org/packages/77/1c/260c65320df47fee582d78ad52d49d4195c5439a77b62e73306c2de835ea/piper_tts-1.4.2-cp39-abi3-manylinux_2_17_aarch64.manylinux2014_aarch64.manylinux_2_28_aarch64.whl
ARG PIPER_ARM64_SHA256=6736329f1ef58c39272215849dffdacae601201480b08a0c892938fa4d7c8c67
ARG PIPER_LICENSE_SHA256=0ae0485a5bd37a63e63603596417e4eb0e653334fa6c7f932ca3a0e85d4af227
RUN set -eux; \
  case "${TARGETARCH}" in \
    amd64) piper_url="${PIPER_AMD64_URL}"; piper_sha256="${PIPER_AMD64_SHA256}" ;; \
    arm64) piper_url="${PIPER_ARM64_URL}"; piper_sha256="${PIPER_ARM64_SHA256}" ;; \
    *) echo "Unsupported Piper target architecture: ${TARGETARCH}" >&2; exit 2 ;; \
  esac; \
  piper_wheel="/tmp/${piper_url##*/}"; \
  curl -fsSL "${piper_url}" -o "${piper_wheel}"; \
  printf '%s  %s\n' "${piper_sha256}" "${piper_wheel}" | sha256sum -c -; \
  python3 -m venv /opt/piper; \
  /opt/piper/bin/pip install --no-cache-dir "${piper_wheel}"; \
  test "$(/opt/piper/bin/python -c 'import importlib.metadata; print(importlib.metadata.version("piper-tts"))')" = "${PIPER_VERSION}"; \
  mkdir -p /opt/textus/licenses; \
  curl -fsSL "https://raw.githubusercontent.com/OHF-Voice/piper1-gpl/v${PIPER_VERSION}/COPYING" -o /opt/textus/licenses/piper-COPYING; \
  printf '%s  %s\n' "${PIPER_LICENSE_SHA256}" /opt/textus/licenses/piper-COPYING | sha256sum -c -; \
  rm -f "${piper_wheel}"

COPY piper-models.json /opt/textus/models/piper/manifest.json
RUN set -eux; \
  base="https://huggingface.co/rhasspy/piper-voices/resolve/${PIPER_VOICES_REF}/en/en_US"; \
  install_voice() { \
    voice="$1"; model_sha="$2"; config_sha="$3"; card_sha="$4"; \
    model_id="en_US-${voice}-medium"; target="/opt/textus/models/piper/${model_id}"; \
    mkdir -p "${target}"; \
    curl -fsSL "${base}/${voice}/medium/${model_id}.onnx" -o "${target}/${model_id}.onnx"; \
    curl -fsSL "${base}/${voice}/medium/${model_id}.onnx.json" -o "${target}/${model_id}.onnx.json"; \
    curl -fsSL "${base}/${voice}/medium/MODEL_CARD" -o "${target}/MODEL_CARD"; \
    printf '%s  %s\n' "${model_sha}" "${target}/${model_id}.onnx" | sha256sum -c -; \
    printf '%s  %s\n' "${config_sha}" "${target}/${model_id}.onnx.json" | sha256sum -c -; \
    printf '%s  %s\n' "${card_sha}" "${target}/MODEL_CARD" | sha256sum -c -; \
  }; \
  install_voice ljspeech \
    6f52a751e2349abe7a76735eb09dc1875298c77ea2342ffd2fef79ff81b87f22 \
    141d612cc0a95ed7efc1ca936b845c2364967f2e9217c5dbfcf69fc4d6c65860 \
    fbee1529c89d36b3fe76d7e9f3f832dce17f44900a52d76a9bda735654766b4d; \
  install_voice joe \
    58afce0321b8d9c46d7cdf9c16500cc55a793b4220212dba6b70fb788b3baf06 \
    3d6d5410b3795cb1950595247ef8f06190719e6fdbfa3a2356d8ec368e1aad33 \
    d2caa63aca0fccb155105e959e393e5c0c0f03a1f388ef5ba217b83ef860c760; \
  /opt/piper/bin/python -c 'import json; d=json.load(open("/opt/textus/models/piper/manifest.json")); assert d["runtime"]["version"] == "1.4.2"; assert d["repository"]["revision"] == "5b44ec7bab7c5822cfec48fbd5aa99db71a823d6"; assert len(d["models"]) == 2'

COPY textus-toolchain /usr/local/bin/textus-toolchain
RUN chmod +x /usr/local/bin/textus-toolchain

WORKDIR /workspace

ENTRYPOINT ["/usr/local/bin/textus-toolchain"]
CMD ["bash"]
