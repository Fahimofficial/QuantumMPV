#include <jni.h>
#include <curl/curl.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>

#define MAX_BODY_BYTES (2 * 1024 * 1024)

struct buffer {
    char *data;
    size_t len;
    size_t cap;
};

static int buffer_append(struct buffer *b, const char *s, size_t n) {
    if (b->len + n + 1 >= MAX_BODY_BYTES) {
        if (b->len + n + 1 > b->cap) {
            n = MAX_BODY_BYTES - b->len - 1;
            if (n <= 0) return 0;
        }
    }
    size_t needed = b->len + n + 1;
    if (needed > b->cap) {
        size_t new_cap = b->cap ? b->cap * 2 : 4096;
        while (new_cap < needed) new_cap *= 2;
        char *p = realloc(b->data, new_cap);
        if (!p) return -1;
        b->data = p;
        b->cap = new_cap;
    }
    memcpy(b->data + b->len, s, n);
    b->len += n;
    b->data[b->len] = '\0';
    return (int)n;
}

static size_t write_cb(char *ptr, size_t size, size_t nmemb, void *userdata) {
    return (size_t)buffer_append((struct buffer *)userdata, ptr, size * nmemb);
}

static void json_escape(const char *in, char *out, size_t out_size) {
    size_t j = 0;
    for (const char *p = in; *p && j + 6 < out_size; p++) {
        switch (*p) {
            case '"':  out[j++] = '\\'; out[j++] = '"';  break;
            case '\\': out[j++] = '\\'; out[j++] = '\\'; break;
            case '\n': out[j++] = '\\'; out[j++] = 'n';  break;
            case '\r': out[j++] = '\\'; out[j++] = 'r';  break;
            case '\t': out[j++] = '\\'; out[j++] = 't';  break;
            default:   out[j++] = *p;                     break;
        }
    }
    out[j] = '\0';
}

static int json_append(char **json, size_t *pos, size_t *cap, const char *fmt, ...) {
    if (*pos >= *cap) return 0;
    va_list ap;
    va_start(ap, fmt);
    int needed = vsnprintf(*json + *pos, *cap - *pos, fmt, ap);
    va_end(ap);
    if (needed < 0) return 0;
    if ((size_t)needed >= *cap - *pos) {
        size_t new_cap = *cap + (size_t)needed + 1;
        char *p = realloc(*json, new_cap);
        if (!p) return 0;
        *json = p;
        *cap = new_cap;
        va_start(ap, fmt);
        vsnprintf(*json + *pos, *cap - *pos, fmt, ap);
        va_end(ap);
    }
    *pos += (size_t)needed;
    return 1;
}

static char *build_response_json(long status, struct buffer *body,
                                 struct buffer *raw_headers, const char *error) {
    size_t cap = 2048;
    size_t pos = 0;
    char *json = malloc(cap);
    if (!json) return NULL;

    json_append(&json, &pos, &cap, "{\"status\":%ld,\"body\":\"", status);

    if (body && body->data) {
        size_t esc_len = body->len * 6 + 1;
        char *esc = malloc(esc_len);
        if (esc) {
            json_escape(body->data, esc, esc_len);
            json_append(&json, &pos, &cap, "%s", esc);
            free(esc);
        }
    }

    json_append(&json, &pos, &cap, "\",\"headers\":{");

    if (raw_headers && raw_headers->data) {
        char *hdr = strdup(raw_headers->data);
        if (hdr) {
            int first = 1;
            char *save;
            char *line = strtok_r(hdr, "\r\n", &save);
            while (line) {
                if (strncmp(line, "HTTP/", 5) == 0) { line = strtok_r(NULL, "\r\n", &save); continue; }
                char *colon = strchr(line, ':');
                if (colon) {
                    *colon = '\0';
                    char *key = line;
                    char *val = colon + 1;
                    while (*val == ' ') val++;
                    char ek[512], ev[2048];
                    json_escape(key, ek, sizeof(ek));
                    json_escape(val, ev, sizeof(ev));
                    if (!first) json_append(&json, &pos, &cap, ",");
                    first = 0;
                    json_append(&json, &pos, &cap, "\"%s\":\"%s\"", ek, ev);
                }
                line = strtok_r(NULL, "\r\n", &save);
            }
            free(hdr);
        }
    }

    json_append(&json, &pos, &cap, ",\"error\":");
    if (error) {
        char ee[1024];
        json_escape(error, ee, sizeof(ee));
        json_append(&json, &pos, &cap, "\"%s\"", ee);
    } else {
        json_append(&json, &pos, &cap, "null");
    }
    json_append(&json, &pos, &cap, "}");

    return json;
}

static int curl_initialised = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    curl_global_init(CURL_GLOBAL_DEFAULT);
    curl_initialised = 1;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    if (curl_initialised) {
        curl_global_cleanup();
        curl_initialised = 0;
    }
}

JNIEXPORT jstring JNICALL
Java_app_gyrolet_mpvrx_ui_player_ScriptCurlBridge_nativeExecute(
    JNIEnv *env, jclass clazz,
    jstring j_url, jstring j_method,
    jobjectArray j_header_keys, jobjectArray j_header_values,
    jstring j_body, jstring j_content_type, jint j_timeout) {

    const char *url   = j_url   ? (*env)->GetStringUTFChars(env, j_url, NULL) : "";
    const char *meth  = j_method ? (*env)->GetStringUTFChars(env, j_method, NULL) : "GET";
    const char *body  = j_body  ? (*env)->GetStringUTFChars(env, j_body, NULL) : NULL;
    const char *ctype = j_content_type ? (*env)->GetStringUTFChars(env, j_content_type, NULL) : NULL;

    jstring result = NULL;

    CURL *curl = curl_easy_init();
    if (!curl) {
        result = (*env)->NewStringUTF(env,
            "{\"status\":0,\"body\":\"\",\"headers\":{},\"error\":\"Failed to init libcurl\"}");
        goto cleanup_strings;
    }

    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_MAXREDIRS, 10L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, (long)j_timeout);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, (long)j_timeout);

    struct buffer resp_body = {0};
    struct buffer resp_headers = {0};
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &resp_body);
    curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, write_cb);
    curl_easy_setopt(curl, CURLOPT_HEADERDATA, &resp_headers);

    if (strcmp(meth, "GET") == 0) {
        curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
    } else if (strcmp(meth, "HEAD") == 0) {
        curl_easy_setopt(curl, CURLOPT_NOBODY, 1L);
    } else if (strcmp(meth, "POST") == 0) {
        curl_easy_setopt(curl, CURLOPT_POST, 1L);
    } else if (strcmp(meth, "PUT") == 0 || strcmp(meth, "PATCH") == 0 || strcmp(meth, "DELETE") == 0) {
        curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, meth);
    }

    if (body && (strcmp(meth, "POST") == 0 || strcmp(meth, "PUT") == 0 || strcmp(meth, "PATCH") == 0)) {
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body);
        curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long)strlen(body));
    }

    struct curl_slist *chunk = NULL;
    if (ctype) {
        char h[512];
        snprintf(h, sizeof(h), "Content-Type: %s", ctype);
        chunk = curl_slist_append(chunk, h);
    }

    jsize hcount = j_header_keys ? (*env)->GetArrayLength(env, j_header_keys) : 0;
    for (int i = 0; i < hcount; i++) {
        jstring jk = (*env)->GetObjectArrayElement(env, j_header_keys, i);
        jstring jv = (*env)->GetObjectArrayElement(env, j_header_values, i);
        const char *k = (*env)->GetStringUTFChars(env, jk, NULL);
        const char *v = (*env)->GetStringUTFChars(env, jv, NULL);
        char h[4096];
        snprintf(h, sizeof(h), "%s: %s", k, v);
        chunk = curl_slist_append(chunk, h);
        (*env)->ReleaseStringUTFChars(env, jk, k);
        (*env)->ReleaseStringUTFChars(env, jv, v);
        (*env)->DeleteLocalRef(env, jk);
        (*env)->DeleteLocalRef(env, jv);
    }
    if (chunk) curl_easy_setopt(curl, CURLOPT_HTTPHEADER, chunk);

    CURLcode res = curl_easy_perform(curl);
    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

    const char *err = NULL;
    if (res != CURLE_OK) {
        err = curl_easy_strerror(res);
    }

    char *json = build_response_json(http_code, &resp_body, &resp_headers, err);
    if (json) {
        result = (*env)->NewStringUTF(env, json);
        free(json);
    } else {
        result = (*env)->NewStringUTF(env,
            "{\"status\":0,\"body\":\"\",\"headers\":{},\"error\":\"OOM building response\"}");
    }

    curl_easy_cleanup(curl);
    if (chunk) curl_slist_free_all(chunk);
    free(resp_body.data);
    free(resp_headers.data);

cleanup_strings:
    if (j_url && url)   (*env)->ReleaseStringUTFChars(env, j_url, url);
    if (j_method && meth) (*env)->ReleaseStringUTFChars(env, j_method, meth);
    if (j_body && body)  (*env)->ReleaseStringUTFChars(env, j_body, body);
    if (j_content_type && ctype) (*env)->ReleaseStringUTFChars(env, j_content_type, ctype);

    return result;
}
