# Discord Twemoji country flags

The app bundles a flags-only COLRv1 font made from the 259 regional-indicator
flag SVGs in Discord's Twemoji repository. Ordinary text and other emoji keep
their existing fonts. Country flag spans retain their original Unicode text.

See `NOTICE`, `LICENSE-GRAPHICS`, and `sources.json` for attribution, the pinned
source commit, the source SVG hashes, and the generated font hash. These notices
are also included in the APK assets. Attribution is visible in the app's
Open-source Software and License Notice.

To regenerate with Python 3.13 and nanoemoji 0.16.0:

```powershell
python -m pip install nanoemoji==0.16.0
python support/fonts/build_discord_flags.py --work-dir .codex-twemoji-flags
```

The generator downloads the pinned archive; normal app builds and runtime do
not need network access or the Python tools.
