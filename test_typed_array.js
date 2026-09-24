const typedArray = new Uint8Array([1, 2, 3]);
const res = Object.groupBy(typedArray, x => x % 2 === 0 ? 'even' : 'odd');
console.log(JSON.stringify(res));

// Test typed array buffer detach inside callback
const ta = new Uint8Array(4);
ta.fill(1);
const res2 = Object.groupBy(ta, (x, i) => {
    if (i === 1) {
        try {
            // How to detach array buffer in JS?
            // postMessage or something? QuickJS doesn't have postMessage easily available.
            // Transferrable.
        } catch(e) {}
    }
    return x;
});
console.log(JSON.stringify(res2));
