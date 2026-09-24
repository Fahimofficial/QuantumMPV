const fs = require('fs');

const filePaths = [
    '.github/workflows/instrumented-tests.yml',
    '.github/workflows/pre-release.yml',
    '.github/workflows/preview.yml',
    '.github/workflows/build.yml',
    '.github/workflows/ci.yml',
    '.github/workflows/release.yml'
];

for (const path of filePaths) {
    if (fs.existsSync(path)) {
        let code = fs.readFileSync(path, 'utf8');
        const s1 = `uses: android-actions/setup-android@v4`;
        const n1 = `uses: android-actions/setup-android@v4\n        with:\n          cmdline-tools-version: 11479570`;
        // but only if it's not already there
        if (!code.includes('cmdline-tools-version: 11479570')) {
             code = code.replace(s1, n1);
             fs.writeFileSync(path, code);
             console.log("Patched " + path);
        }
    }
}
