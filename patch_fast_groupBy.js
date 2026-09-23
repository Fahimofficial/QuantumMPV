const fs = require('fs');
const path = 'app/src/main/cpp/third_party/quickjs/quickjs.c';
let code = fs.readFileSync(path, 'utf8');

const s1 = `JSArrayIteratorData *it = JS_VALUE_GET_OBJ(iter)->u.array_iterator_data;`;
const n1 = `struct JSArrayIteratorData *it = JS_VALUE_GET_OBJ(iter)->u.array_iterator_data;`;
code = code.replace(s1, n1);

const s2 = `JSArrayIteratorData *it = iter_p->u.array_iterator_data;`;
const n2 = `struct JSArrayIteratorData *it = iter_p->u.array_iterator_data;`;
code = code.replace(s2, n2);

// Wait, the struct is not defined yet at line 41659?
// Let's check where JSArrayIteratorData is defined.
// It is defined at line 44929:
// typedef struct JSArrayIteratorData {
//    JSValue obj;
//    JSIteratorKindEnum kind;
//    uint32_t idx;
// } JSArrayIteratorData;
// BUT js_object_groupBy is defined at line 41618 !!
// This means JSArrayIteratorData is NOT visible inside js_object_groupBy!
// We need to move the typedef of JSArrayIteratorData to the top of the file, or above js_object_groupBy!
// Or we can just read the values using `JS_GetOpaque`.
// No, JSArrayIteratorData is defined lower in the file.
// We can just move the struct definition.

const typedefStr = `typedef struct JSArrayIteratorData {
    JSValue obj;
    JSIteratorKindEnum kind;
    uint32_t idx;
} JSArrayIteratorData;`;

code = code.replace(typedefStr, ""); // remove it from line 44929

// Insert it above js_object_groupBy
const insertionPoint = `static JSValue js_object_groupBy(JSContext *ctx, JSValueConst this_val,`;
code = code.replace(insertionPoint, typedefStr + "\n\n" + insertionPoint);

fs.writeFileSync(path, code);
