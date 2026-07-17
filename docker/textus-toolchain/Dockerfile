ARG KROKI_IMAGE=yuzutech/kroki:latest
FROM ${KROKI_IMAGE}

ARG ANTORA_VERSION=3.1.15
ARG ASCIIDOCTOR_KROKI_VERSION=0.18.1
ARG PLAYWRIGHT_VERSION=1.61.1
ARG REMOTION_VERSION=4.0.490

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

COPY textus-toolchain /usr/local/bin/textus-toolchain
RUN chmod +x /usr/local/bin/textus-toolchain

WORKDIR /workspace

ENTRYPOINT ["/usr/local/bin/textus-toolchain"]
CMD ["bash"]
