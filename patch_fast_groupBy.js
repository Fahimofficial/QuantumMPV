const fs = require('fs');
const path = 'app/src/main/cpp/third_party/quickjs/quickjs.c';
let code = fs.readFileSync(path, 'utf8');

const s1 = `        return groups;
        }
    }

    for (idx = 0; ; idx++) {`;

const n1 = `        return groups;
    }

    for (idx = 0; ; idx++) {`;

code = code.replace(s1, n1);
fs.writeFileSync(path, code);
