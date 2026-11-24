# iptv-org TV (Android 9+ / Android TV & TV Box)

Lightweight, ad-free native Android client designed for **Android TV** and **TV Boxes** (Amlogic, Allwinner, Rockchip, etc.) to browse and play public streams from [iptv-org](https://github.com).

---

## Features
* **Direct `iptv-org` Integration:** Browse channels grouped by **Country**, **Category**, or **Language** (`index.country.m3u`, `index.category.m3u`, `index.language.m3u`).
* **Smart 24-Hour Local Cache:** Fast startup and reduced bandwidth usage with a 15-second download timeout and one-click manual refresh (**Atualizar**).
* **Built-in Media3 ExoPlayer + External Player Support:** Plays HLS/M3U8 streams smoothly inside the app or hands off any stream to external players (Next Player, VLC, MX Player, IPTV Pro) via **Player Ext**.
* **TV Remote / D-Pad Friendly:** Designed in landscape mode with Leanback launcher support, quick search bar, and D-Pad focus navigation.
* **Broad Compatibility:** Compiled for **Android 9.0 Pie (API 28)** up to **Android 14+ (API 34+)**.

---

## Download & Installation
1. Go to the [**Actions**](../../actions) tab (or [**Releases**](../../releases) if published).
2. Open the latest green workflow run (**Build Universal IPTV APK**) and download **`iptv-org-TV-Android9+`** under **Artifacts**.
3. Extract `app-release.apk` and install it on your Android TV / TV Box (`adb install -r app-release.apk` or via any file manager).

---

## Resumo em Português
Aplicativo nativo leve e sem anúncios feito para **TV Boxes e Android TV (Android 9 ao 14+)** para assistir às listas públicas do projeto **iptv-org**:
* Filtra canais por **País** (ex.: Brasil), **Categoria** ou **Idioma**, com busca instantânea e cache local de 24 horas.
* Possui player interno **Media3 ExoPlayer** e botão **Player Ext** para abrir o canal em players externos instalados na TV Box.
* Navegação otimizada para controle remoto (**D-Pad**).

---

## Disclaimer
This project does **not** host, provide, or distribute any media content or streams. It is simply an open-source player interface for publicly available playlists maintained by the `iptv-org` community.
