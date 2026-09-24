const arr = [1, 2, 3, 4, 5, 6];
const res = Object.groupBy(arr, x => x % 2 === 0 ? 'even' : 'odd');
console.log(JSON.stringify(res));

// Test 2: modify array during iteration
let c = 0;
const arr2 = [1, 2, 3, 4];
const res2 = Object.groupBy(arr2, function(x) {
    if (c++ === 1) arr2.length = 1;
    return x;
});
console.log("length modified res: ", JSON.stringify(res2));

// Test 3: custom iterator
let c3 = 0;
const iterObj = {
    [Symbol.iterator]: function*() {
        yield 100;
        yield 200;
    }
};
const res3 = Object.groupBy(iterObj, x => 'group');
console.log("custom iterator res: ", JSON.stringify(res3));

// Test 4: intercept built-in iterator
const origValues = Array.prototype.values;
let savedIter = null;
Array.prototype[Symbol.iterator] = function() {
    savedIter = origValues.call(this);
    return savedIter;
}
let res4 = Object.groupBy([10, 20], x => {
    savedIter.next(); // manually consume
    return 'group';
});
console.log("intercepted iter res: ", JSON.stringify(res4));
