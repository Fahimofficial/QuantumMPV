1. **Add `realm` to function records**:
   - Update `JSCFunctionDataRecord` and `JSCClosureRecord` structures in `quickjs.c` to include `JSContext *realm;`.
2. **Set `realm` during creation**:
   - In `JS_NewCFunctionData2` and `JS_NewCClosure`, assign `s->realm = ctx;` when allocating and initializing the records.
3. **Switch realms on invocation**:
   - In `js_call_c_function_data` and `js_call_c_closure`, assign `ctx = s->realm;` after pushing the stack frame (just like `js_call_c_function` does).
   - Remove the `// TODO(bnoordhuis)` comments.
4. **Update `JS_GetFunctionRealm`**:
   - Add cases for `JS_CLASS_C_FUNCTION_DATA` and `JS_CLASS_C_CLOSURE` in the switch statement of `JS_GetFunctionRealm` to return the associated realm, ensuring engine internals know about these realms.
5. **Pre-commit Steps**:
   - Run required checks to ensure proper testing, verification, review, and reflection are done.
6. **Submit**:
   - Commit the changes and submit the branch.
