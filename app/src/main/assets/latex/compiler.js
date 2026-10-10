import { BusyTexRunner, XeLatex } from './vendor/index.js';

// This origin is served exclusively from packaged assets by the Android client.
// No remote endpoint, shell execution, uploaded HTML or model output is used.
const base = '/latex/vendor/busytex';
let lastPercent = -1;
const runner = new BusyTexRunner({
    busytexBasePath: base,
    preloadDataPackages: [`${base}/texlive-basic.js`, `${base}/texlive-recommended.js`],
    catalogDataPackages: [],
    initRetries: 1,
    verbose: false,
    onDownloadProgress: ({ percent }) => {
        const value = Math.max(0, Math.min(100, Math.floor(percent)));
        if (value !== lastPercent) {
            lastPercent = value;
            PrescriptionBridge.stage(`Preparing offline LaTeX packages: ${value}%`);
        }
    }
});
window.cancelCompilation = () => runner.terminate();
try {
    PrescriptionBridge.stage('Loading offline LaTeX compiler and packages');
    await runner.initialize(true);
    PrescriptionBridge.stage('Typesetting prescription with XeLaTeX');
    const additionalFiles = await Promise.all(['NotoSans-Regular.ttf', 'NotoSans-Bold.ttf', 'NotoSansDevanagari-Regular.ttf'].map(async name => {
        const response = await fetch(`/latex/vendor/fonts/${name}`);
        if (!response.ok) throw new Error('Missing packaged font');
        return { path: name, content: new Uint8Array(await response.arrayBuffer()) };
    }));
    const result = await new XeLatex(runner).compile({
        input: PrescriptionBridge.source(), additionalFiles,
        bibtex: false, biber: false, makeindex: false, rerun: true,
        shellEscape: false, verbose: 'silent'
    });
    if (!result.success || !result.pdf || result.pdf.byteLength > 8 * 1024 * 1024) {
        throw new Error('LaTeX compilation failed');
    }
    PrescriptionBridge.stage('Finishing prescription PDF');
    let binary = '';
    for (let index = 0; index < result.pdf.length; index += 8192) {
        binary += String.fromCharCode(...result.pdf.subarray(index, index + 8192));
    }
    PrescriptionBridge.complete(btoa(binary));
} catch (_) {
    // Compiler logs can contain patient text. Never send raw logs to Logcat.
    PrescriptionBridge.failed();
} finally {
    runner.terminate();
}
