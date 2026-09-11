# Third-Party Notices

This file records the licenses of third-party material that ships inside the Logic Labs
APK or its source repository. The project itself is licensed **GPL-3.0-or-later**
(see [LICENSE](LICENSE)).

---

## 1. Bundled font software (SIL Open Font License 1.1)

The app bundles two font families as compiled assets under
`core-designsystem/src/main/res/font/`. Both are licensed under the SIL Open Font
License, Version 1.1, which permits bundling with GPL-licensed software.

### Inter

- File: `core-designsystem/src/main/res/font/inter_semibold.ttf` (variable font)
- Version: 4.001 (git-66647c0bb)
- Project: https://github.com/rsms/inter
- Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter)

### JetBrains Mono

- File: `core-designsystem/src/main/res/font/jetbrains_mono_medium.ttf`
- Version: 2.305 (ttfautohint v1.8.4.7-5d5b)
- Project: https://github.com/JetBrains/JetBrainsMono
- Copyright 2020 The JetBrains Mono Project Authors (https://github.com/JetBrains/JetBrainsMono)

### SIL Open Font License, Version 1.1 — full text

```
-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OF OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```

---

## 2. Derived work: Digital simulation kernel (GPL-3.0)

The `:core-digital` module is a headless extraction of the event-driven simulation
kernel from **hneemann/Digital** by Helmut Neemann and contributors:

- Upstream: https://github.com/hneemann/Digital
- License: GPL-3.0 (all 184 extracted Java files retain their upstream copyright
  headers; the upstream license text is identical to this repository's [LICENSE](LICENSE))

This derivation is why the application as a whole is GPL-3.0-or-later.

---

## 3. Build-time dependencies (not vendored)

All dependencies are resolved at build time from Google Maven / Maven Central and
carry their own license metadata upstream:

| Dependency | SPDX license |
|---|---|
| AndroidX libraries (core-ktx, activity, lifecycle, navigation, datastore, splashscreen, compose, annotation, collection, arch, savedstate, profileinstaller, startup, tracing, interpolator, versionedparcelable) | Apache-2.0 |
| Kotlin stdlib, kotlinx-coroutines, kotlinx-serialization | Apache-2.0 |
| org.slf4j:slf4j-api | MIT |
| com.squareup.okio:okio | Apache-2.0 |
| com.google.guava:listenablefuture | Apache-2.0 |
