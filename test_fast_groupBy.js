const arr = [1, 2, 3, 4, 5, 6];
const res = Object.groupBy(arr, x => x % 2 === 0 ? 'even' : 'odd');
console.log(JSON.stringify(res));

arr[Symbol.iterator] = function*() {
  yield 10;
  yield 20;
};
const res2 = Object.groupBy(arr, x => 'same');
console.log(JSON.stringify(res2));
