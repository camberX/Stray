# Third-party code in the CS2 knives feature

Parts of this package are ported from or based on the projects below.

| Project | Used for | License |
| --- | --- | --- |
| [ValveResourceFormat](https://github.com/ValveResourceFormat/ValveResourceFormat) | VPK, resource blocks, KV3, vtex, models, NmClip/NmSkeleton decoding | MIT |
| [meshoptimizer](https://github.com/zeux/meshoptimizer) | vertex and index buffer decoding (`s2/Meshopt.java`) | MIT |
| [DirectXTex](https://github.com/microsoft/DirectXTex) | BC7 partition and index tables (`s2/Bcn.java`) | MIT |
| [aircompressor](https://github.com/airlift/aircompressor) | Zstandard decoder (`s2/zstd/`) | Apache 2.0 (see the file headers) |

The Counter-Strike 2 knife data (models, textures, animations) is not part of this repository's
code; it is published separately on the `cs2-data` branch with permission from Valve, for
non-commercial use.

## MIT License (ValveResourceFormat, meshoptimizer, DirectXTex)

Copyright (c) ValveResourceFormat contributors; Copyright (c) Arseny Kapoulkine;
Copyright (c) Microsoft Corporation.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT
OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
