#include <stdio.h>
enum {
    JS_ATOM_TYPE_STRING = 1,
    JS_ATOM_TYPE_GLOBAL_SYMBOL,
    JS_ATOM_TYPE_SYMBOL,
    JS_ATOM_TYPE_PRIVATE,
};
int main() {
    printf("JS_ATOM_TYPE_STRING = %d\n", JS_ATOM_TYPE_STRING);
    printf("JS_ATOM_TYPE_GLOBAL_SYMBOL = %d\n", JS_ATOM_TYPE_GLOBAL_SYMBOL);
    printf("JS_ATOM_TYPE_SYMBOL = %d\n", JS_ATOM_TYPE_SYMBOL);
    printf("JS_ATOM_TYPE_PRIVATE = %d\n", JS_ATOM_TYPE_PRIVATE);
    return 0;
}
