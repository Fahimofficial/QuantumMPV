1. Modify `js_atom_concat_str` in `app/src/main/cpp/third_party/quickjs/quickjs.c`.
2. Replace it with the following code:
```c
static JSAtom js_atom_concat_str(JSContext *ctx, JSAtom name, const char *str1)
{
    JSValue str;
    JSAtom atom;

    str = JS_AtomToString(ctx, name);
    if (JS_IsException(str))
        return JS_ATOM_NULL;
    str = JS_ConcatString(ctx, str, JS_NewString(ctx, str1));
    if (JS_IsException(str))
        return JS_ATOM_NULL;
    atom = JS_ValueToAtom(ctx, str);
    JS_FreeValue(ctx, str);
    return atom;
}
```
3. `JS_ConcatString` will free both `str` (from `JS_AtomToString`) and the new string from `JS_NewString(ctx, str1)`, and return a new `JSValue` representing the concatenated string.
4. Then `JS_ValueToAtom` takes the `JSValue` (without freeing it), returning a `JSAtom`.
5. Finally, we free the concatenated `JSValue` (`str`) and return the atom.
6. The `// TODO(chqrlie): use string concatenation instead of UTF-8 conversion` comment is resolved and should be removed.

7. Pre-commit step: Ensure tests run correctly before pushing.
