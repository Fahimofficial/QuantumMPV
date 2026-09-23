#include "quickjs.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void test_roundtrip(JSContext *ctx, const char *script) {
    JSValue obj = JS_Eval(ctx, script, strlen(script), "test.js", JS_EVAL_TYPE_GLOBAL | JS_EVAL_FLAG_COMPILE_ONLY);
    if (JS_IsException(obj)) {
        JS_FreeValue(ctx, obj);
        printf("Eval failed\n");
        return;
    }
    size_t size;
    uint8_t *bc = JS_WriteObject(ctx, &size, obj, JS_WRITE_OBJ_BYTECODE);
    JS_FreeValue(ctx, obj);

    if (!bc) {
        printf("Serialization failed\n");
        return;
    }

    JSValue obj2 = JS_ReadObject(ctx, bc, size, JS_READ_OBJ_BYTECODE);
    js_free(ctx, bc);

    if (JS_IsException(obj2)) {
        JS_FreeValue(ctx, obj2);
        printf("Deserialization failed\n");
        return;
    }

    JS_FreeValue(ctx, obj2);
    printf("Roundtrip successful\n");
}

void test_malformed_tag(JSContext *ctx) {
    // Generate valid bytecode first
    const char *script = "var x = 1;";
    JSValue obj = JS_Eval(ctx, script, strlen(script), "test.js", JS_EVAL_TYPE_GLOBAL | JS_EVAL_FLAG_COMPILE_ONLY);
    size_t size;
    uint8_t *bc = JS_WriteObject(ctx, &size, obj, JS_WRITE_OBJ_BYTECODE);
    JS_FreeValue(ctx, obj);

    if (!bc) return;

    // Mutate the type byte of an atom (assuming standard structure: version, checksum, atom count leb128, then atom types)
    // We just brutally mutate bytes looking for the first atom type byte (0)
    // Version is 1 byte, Checksum is 4 bytes, count is leb128 (1 byte usually for small scripts)
    if (size > 6) {
        bc[6] = 5; // Invalid tag > JS_ATOM_TYPE_PRIVATE
    }

    JSValue obj2 = JS_ReadObject(ctx, bc, size, JS_READ_OBJ_BYTECODE);
    js_free(ctx, bc);

    if (JS_IsException(obj2)) {
        JS_FreeValue(ctx, obj2);
        printf("Malformed tag rejected\n");
    } else {
        JS_FreeValue(ctx, obj2);
        printf("Malformed tag ACCEPTED - FAILURE\n");
    }
}

void test_version_rejection(JSContext *ctx) {
    // Generate valid bytecode first
    const char *script = "var x = 1;";
    JSValue obj = JS_Eval(ctx, script, strlen(script), "test.js", JS_EVAL_TYPE_GLOBAL | JS_EVAL_FLAG_COMPILE_ONLY);
    size_t size;
    uint8_t *bc = JS_WriteObject(ctx, &size, obj, JS_WRITE_OBJ_BYTECODE);
    JS_FreeValue(ctx, obj);

    if (!bc) return;

    // Mutate the version byte
    bc[0] = 0; // Invalid version

    JSValue obj2 = JS_ReadObject(ctx, bc, size, JS_READ_OBJ_BYTECODE);
    js_free(ctx, bc);

    if (JS_IsException(obj2)) {
        JS_FreeValue(ctx, obj2);
        printf("Version mismatch rejected\n");
    } else {
        JS_FreeValue(ctx, obj2);
        printf("Version mismatch ACCEPTED - FAILURE\n");
    }
}

int main() {
    JSRuntime *rt = JS_NewRuntime();
    JSContext *ctx = JS_NewContext(rt);

    test_roundtrip(ctx, "function test() { return 'string'; }"); // String
    test_roundtrip(ctx, "({ a: 1, b: 'string', [Symbol.iterator]: function(){} })"); // Symbol and properties
    test_roundtrip(ctx, "({ 42: 'x' })"); // Tagged integer atom
    test_roundtrip(ctx, "var x = 42;"); // Constant keyword-ish atoms

    test_malformed_tag(ctx);
    test_version_rejection(ctx);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
    return 0;
}
