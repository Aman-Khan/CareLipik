# Phase 1 prescription generation

After the clinical report is generated, **Continue to report and prescription** opens a new screen. Its Report tab shows the generated sections; Edit report returns to the existing editor. The Prescription tab provides a common A4 prescription template.

Existing report medicine entries and recognized medicine names in the reviewed transcript appear as **mentions**, not clinical recommendations. Phase 1 recognizes a small common-name list and explicit tablet/capsule/syrup/injection cues. Unrecognized names can be entered manually. Historical, negated and patient-reported mentions are deliberately not interpreted as prescriptions. No medicine is added automatically and no missing dosage is inferred. The doctor selects a mention or adds a medicine, edits its fields, enters a positive quantity/count, and confirms review before creating a PDF.

The template includes clinic, doctor/registration, patient/age, date, medicine names and directions, quantity, advice, follow-up and a signature line. User-entered text is escaped before typesetting. Latin and Devanagari fonts are packaged. A generated PDF is invalidated after any edit or confirmation change.

## Actual offline LaTeX compilation

The application packages **texlyre-busytex 1.4.0**, its **assets-v1.4.0 / TeX Live 2026** bundle, and Noto Latin/Devanagari fonts. XeLaTeX generates XDV and xdvipdfmx produces the PDF in a WebView Web Worker. Android `PdfDocument` is not used for prescriptions. All compiler/package/font requests are served from APK assets; other requests are rejected. Shell escape is disabled. No clinical input is sent to a server. Only fixed progress messages and failure status are logged under `CareLipikPrescription`; compiler logs containing text are not exposed through Logcat.

The complete operation has a three-minute timeout and cancellation destroys the worker's WebView. The generated PDF and source live in private temporary cache, with stale files removed after 24 hours on the next generation. **Download prescription PDF** and **Download LaTeX source** use Android's document picker. There is no storage permission requirement. These are session documents in phase 1; prescriptions are not yet attached to encrypted consultation history, and downloading a prescription does not approve the clinical report.

## Build setup

Python 3.11+ is required for the packaging script, alongside the existing Android build tools. Before the first offline build, populate the runtime cache:

```powershell
./gradlew :app:prepareLatexRuntime
./gradlew test lint assembleDebug -Pcarelipik.includeQwen3=true --offline
```

`prepareLatexRuntime` runs automatically before app builds. Downloads are build-time only, pinned and SHA-256 checked; the large upstream asset archive contains more packages than we ship. We package only the basic/recommended TeX data, compiler and required fonts. Cache: `tmp/latex-cache/`. Packaged output: `app/build/generated/latex-assets/`. Both are ignored build inputs, not source-controlled binaries. The APK grows because it includes the offline TeX distribution.

Exported `.tex` files use XeLaTeX and expect `NotoSans-Regular.ttf`, `NotoSans-Bold.ttf` and `NotoSansDevanagari-Regular.ttf` in their working directory, plus geometry/fontspec/longtable/array packages. Those fonts are obtained from the pinned/checksummed build cache. The standalone source is optional; no desktop TeX installation is needed to create the PDF on the phone.

## Third-party source and notices

- BusyTeX API 1.4.0: https://github.com/TeXlyre/texlyre-busytex (AGPL-3.0; its license is packaged as `BUSYTEX-LICENSE.txt`).
- Compiler/bundle build source: https://github.com/TeXlyre/texlyre-busytex-build ; release https://github.com/TeXlyre/texlyre-busytex/releases/tag/assets-v1.4.0 . TeX Live has component-specific licenses retained in its data packages. Observe upstream source/license requirements when distributing the app.
- Noto fonts: https://github.com/notofonts/noto-fonts (SIL Open Font License 1.1; notice in `tools/latex_runtime/NOTO-OFL.txt` and packaged with the fonts).

Phone verification should cover tab switching, adding/removing a mentioned and manual medicine, quantity validation, editing after generation, cancellation, offline generation, and saving/opening the PDF through the system picker. Do not use actual patient details for diagnostics or screenshots.
