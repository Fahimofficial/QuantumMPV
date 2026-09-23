const fs = require('fs');
const path = 'app/src/main/cpp/third_party/quickjs/quickjs.c';
let code = fs.readFileSync(path, 'utf8');

const targetFunctionStr = `static JSValue js_object_groupBy(JSContext *ctx, JSValueConst this_val,
                                 int argc, JSValueConst *argv)
{
    JSValue res, iter, next, groups, k, v, prop;
    JSValueConst cb, args[2];
    int64_t idx;
    int done;

    // "is function?" check must be observed before argv[0] is accessed
    cb = argv[1];
    if (check_function(ctx, cb))
        return JS_EXCEPTION;

    // TODO(bnoordhuis) add fast path for arrays but as groupBy() is
    // defined in terms of iterators, the fast path must check that
    // this[Symbol.iterator] is the built-in array iterator
    iter = JS_GetIterator(ctx, argv[0], /*is_async*/false);
    if (JS_IsException(iter))
        return JS_EXCEPTION;

    k = JS_UNDEFINED;
    v = JS_UNDEFINED;
    prop = JS_UNDEFINED;
    groups = JS_UNDEFINED;

    next = JS_GetProperty(ctx, iter, JS_ATOM_next);
    if (JS_IsException(next))
        goto exception;

    groups = JS_NewObjectProto(ctx, JS_NULL);
    if (JS_IsException(groups))
        goto exception;

    for (idx = 0; ; idx++) {
        v = JS_IteratorNext(ctx, iter, next, 0, NULL, &done);
        if (JS_IsException(v))
            goto exception;
        if (done)
            break; // v is JS_UNDEFINED

        args[0] = v;
        args[1] = js_int64(idx);
        k = JS_Call(ctx, cb, ctx->global_obj, 2, args);
        if (JS_IsException(k))
            goto exception;

        k = js_dup(k);
        prop = JS_GetPropertyValue(ctx, groups, k);
        if (JS_IsException(prop))
            goto exception;

        if (JS_IsUndefined(prop)) {
            prop = JS_NewArray(ctx);
            if (JS_IsException(prop))
                goto exception;
            k = js_dup(k);
            prop = js_dup(prop);
            if (JS_SetPropertyValue(ctx, groups, k, prop,
                                    JS_PROP_C_W_E|JS_PROP_THROW) < 0) {
                goto exception;
            }
        }

        res = js_array_push(ctx, prop, 1, vc(&v), /*unshift*/0);
        if (JS_IsException(res))
            goto exception;
        // res is an int64

        JS_FreeValue(ctx, prop);
        JS_FreeValue(ctx, k);
        JS_FreeValue(ctx, v);
        prop = JS_UNDEFINED;
        k = JS_UNDEFINED;
        v = JS_UNDEFINED;
    }

    JS_FreeValue(ctx, iter);
    JS_FreeValue(ctx, next);
    return groups;

exception:
    JS_FreeValue(ctx, prop);
    JS_FreeValue(ctx, k);
    JS_FreeValue(ctx, v);
    JS_FreeValue(ctx, groups);
    JS_FreeValue(ctx, iter);
    JS_FreeValue(ctx, next);
    return JS_EXCEPTION;
}`;

const newFunctionStr = `typedef struct JSArrayIteratorData {
    JSValue obj;
    JSIteratorKindEnum kind;
    uint32_t idx;
} JSArrayIteratorData;

static JSValue js_object_groupBy(JSContext *ctx, JSValueConst this_val,
                                 int argc, JSValueConst *argv)
{
    JSValue res, iter, next, groups, k, v, prop, key;
    JSValueConst cb, args[2];
    int64_t idx;
    int done;
    int is_array_iterator = 0;

    // "is function?" check must be observed before argv[0] is accessed
    cb = argv[1];
    if (check_function(ctx, cb))
        return JS_EXCEPTION;

    // TODO(bnoordhuis) add fast path for arrays but as groupBy() is
    // defined in terms of iterators, the fast path must check that
    // this[Symbol.iterator] is the built-in array iterator
    iter = JS_GetIterator(ctx, argv[0], /*is_async*/false);
    if (JS_IsException(iter))
        return JS_EXCEPTION;

    k = JS_UNDEFINED;
    v = JS_UNDEFINED;
    prop = JS_UNDEFINED;
    groups = JS_UNDEFINED;
    key = JS_UNDEFINED;

    next = JS_GetProperty(ctx, iter, JS_ATOM_next);
    if (JS_IsException(next))
        goto exception;

    groups = JS_NewObjectProto(ctx, JS_NULL);
    if (JS_IsException(groups))
        goto exception;

    // Fast path for arrays:
    if (JS_VALUE_GET_TAG(iter) == JS_TAG_OBJECT) {
        JSObject *iter_p = JS_VALUE_GET_OBJ(iter);
        if (iter_p->class_id == JS_CLASS_ARRAY_ITERATOR) {
            JSCFunctionType ft2 = { .iterator_next = js_array_iterator_next };
            if (JS_IsCFunction(ctx, next, ft2.generic, 0)) {
                struct JSArrayIteratorData *it = iter_p->u.array_iterator_data;
                // Only enter the direct array fast path when the input uses the built-in values iterator
                // and the created iterator is guaranteed to be fresh, unexposed, positioned at zero,
                // and backed by the same ordinary array as argv[0].
                if (it && it->kind == JS_ITERATOR_KIND_VALUE && it->idx == 0 && JS_VALUE_GET_PTR(it->obj) == JS_VALUE_GET_PTR(argv[0])) {
                    is_array_iterator = 1;
                }
            }
        }
    }

    if (is_array_iterator && JS_VALUE_GET_TAG(argv[0]) == JS_TAG_OBJECT) {
        JSObject *p = JS_VALUE_GET_OBJ(argv[0]);
        for (idx = 0; ; idx++) {
            uint32_t len;
            if (js_get_length32(ctx, &len, argv[0]))
                goto exception_close;
            if (idx >= len)
                break;
            if (!js_get_fast_array_element(ctx, p, idx, &v)) {
                v = JS_GetPropertyInt64(ctx, argv[0], idx);
                if (JS_IsException(v))
                    goto exception_close;
            }

            // Advance internal iterator index to reflect consumption in case
            // the callback has access to the iterator object.
            struct JSArrayIteratorData *it = JS_VALUE_GET_OBJ(iter)->u.array_iterator_data;
            if (it) it->idx = idx + 1;

            args[0] = v;
            args[1] = js_int64(idx);
            k = JS_Call(ctx, cb, JS_UNDEFINED, 2, args);
            if (JS_IsException(k))
                goto exception_close;

            key = JS_ToPropertyKey(ctx, k);
            JS_FreeValue(ctx, k);
            k = JS_UNDEFINED;
            if (JS_IsException(key))
                goto exception_close;

            prop = JS_GetPropertyValue(ctx, groups, js_dup(key));
            if (JS_IsException(prop))
                goto exception_close;

            if (JS_IsUndefined(prop)) {
                prop = JS_NewArray(ctx);
                if (JS_IsException(prop))
                    goto exception_close;
                if (JS_SetPropertyValue(ctx, groups, js_dup(key), js_dup(prop),
                                        JS_PROP_C_W_E|JS_PROP_THROW) < 0) {
                    goto exception_close;
                }
            }

            res = js_array_push(ctx, prop, 1, vc(&v), /*unshift*/0);
            if (JS_IsException(res))
                goto exception_close;
            JS_FreeValue(ctx, res);

            JS_FreeValue(ctx, prop);
            JS_FreeValue(ctx, key);
            JS_FreeValue(ctx, v);
            prop = JS_UNDEFINED;
            key = JS_UNDEFINED;
            v = JS_UNDEFINED;
        }
        JS_FreeValue(ctx, iter);
        JS_FreeValue(ctx, next);
        return groups;
    }

    for (idx = 0; ; idx++) {
        v = JS_IteratorNext(ctx, iter, next, 0, NULL, &done);
        if (JS_IsException(v))
            goto exception_close;
        if (done)
            break; // v is JS_UNDEFINED

        args[0] = v;
        args[1] = js_int64(idx);
        k = JS_Call(ctx, cb, JS_UNDEFINED, 2, args);
        if (JS_IsException(k))
            goto exception_close;

        key = JS_ToPropertyKey(ctx, k);
        JS_FreeValue(ctx, k);
        k = JS_UNDEFINED;
        if (JS_IsException(key))
            goto exception_close;

        prop = JS_GetPropertyValue(ctx, groups, js_dup(key));
        if (JS_IsException(prop))
            goto exception_close;

        if (JS_IsUndefined(prop)) {
            prop = JS_NewArray(ctx);
            if (JS_IsException(prop))
                goto exception_close;
            if (JS_SetPropertyValue(ctx, groups, js_dup(key), js_dup(prop),
                                    JS_PROP_C_W_E|JS_PROP_THROW) < 0) {
                goto exception_close;
            }
        }

        res = js_array_push(ctx, prop, 1, vc(&v), /*unshift*/0);
        if (JS_IsException(res))
            goto exception_close;
        JS_FreeValue(ctx, res);
        // res is an int64

        JS_FreeValue(ctx, prop);
        JS_FreeValue(ctx, key);
        JS_FreeValue(ctx, v);
        prop = JS_UNDEFINED;
        key = JS_UNDEFINED;
        v = JS_UNDEFINED;
    }

    JS_FreeValue(ctx, iter);
    JS_FreeValue(ctx, next);
    return groups;

exception_close:
    if (!JS_IsUndefined(iter))
        JS_IteratorClose(ctx, iter, true);
exception:
    JS_FreeValue(ctx, prop);
    JS_FreeValue(ctx, k);
    JS_FreeValue(ctx, key);
    JS_FreeValue(ctx, v);
    JS_FreeValue(ctx, groups);
    JS_FreeValue(ctx, iter);
    JS_FreeValue(ctx, next);
    return JS_EXCEPTION;
}`;

const structDef = `typedef struct JSArrayIteratorData {
    JSValue obj;
    JSIteratorKindEnum kind;
    uint32_t idx;
} JSArrayIteratorData;`;

code = code.replace(structDef, '');
code = code.replace(targetFunctionStr, newFunctionStr);

fs.writeFileSync(path, code);
