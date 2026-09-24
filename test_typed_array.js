const typedArray = new Uint8Array([1, 2, 3]);
const res = Object.groupBy(typedArray, x => x % 2 === 0 ? 'even' : 'odd');
console.log(JSON.stringify(res));

// Test typed array buffer detach inside callback
const ta = new Uint8Array(4);
ta.fill(1);
try {
    const res2 = Object.groupBy(ta, (x, i) => {
        if (i === 1) {
            // detach buffer! (QuickJS extensions)
            if (typeof Object.detachArrayBuffer === 'function') {
               Object.detachArrayBuffer(ta.buffer);
            } else if (typeof globalThis.gc === 'function') {
                // not sure if quickjs has detach API exposed without explicit test extensions.
            }
        }
        return x;
    });
    console.log(JSON.stringify(res2));
} catch(e) {
    console.log("caught exception: ", e.message);
}
