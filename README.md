# FMHY Cloudstream Extension

This repository contains a Cloudstream 3 provider for indexing the FMHY video directory.

## What it does
- Searches the FMHY `video` directory and indexes public links
- Loads metadata from directory entries
- Extracts direct media links such as `.m3u8`, `.mp4`, `.webm`, and `.mpd`
- Falls back to Cloudstream extractors for supported hosts

## Important note
FMHY is a directory of third-party sites, not a single streaming host. This provider works only for public links and direct media URLs that are exposed in the page markup. It does not bypass Cloudflare, login walls, DRM, or paid streams.

## Build
```bash
gradle make --no-daemon
```

## GitHub Actions
This repository includes a GitHub Actions workflow that builds the Cloudstream plugin and deploys the generated artifact to GitHub Pages.

## Installation
After the workflow runs, add this repository URL to Cloudstream:
https://Ajay902188.github.io/fmhy-cloudstream-extension/repo.json
