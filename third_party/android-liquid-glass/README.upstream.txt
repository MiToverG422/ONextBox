Project: AndroidLiquidGlass (Backdrop)
Upstream: https://github.com/Kyant0/AndroidLiquidGlass
Version: 2.0.1
Branch: kmp
Commit: 65ab177e90e5c1d8c62e70cf7755841982da65f6
License: Apache-2.0 (see LICENSE)

The common rendering sources and Android implementations are combined in the
Android app source set. Multiplatform expect/actual declarations are resolved
to their Android implementations; no other platform is bundled.

The local recording canvas/density handling and controlled draw invalidation
are retained. The bottom navigation keeps ONextBox layout, theme colors and
page selection. Drag clocks use Android uptime and zero-length ranges are
guarded. Kyant Shapes 1.2.1 supplies the current Capsule shape.
