import hashlib, os, re, sys, urllib.request

# Argument 1: Native Library Directory (passed from Java)
native_lib_dir = sys.argv[1] if len(sys.argv) > 1 else ""

# scriptdest is the path for a legacy wrapper if needed
scriptdest = "../youtube-dl.sh"
name = "yt-dlp"
download_name = name + ".download"
repository = "https://github.com/yt-dlp/yt-dlp"
user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

# Clean up legacy and interrupted files, but retain a working yt-dlp until its
# replacement has downloaded and verified successfully.
for path in (scriptdest, "youtube-dl", download_name):
    try:
        if os.path.exists(path): os.unlink(path)
    except:
        pass


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": user_agent})
    return urllib.request.urlopen(request, timeout=60)


def latest_tag():
    # /releases/latest redirects to /releases/tag/<tag>; pin every download to that one tag so the
    # binary and its checksum file always come from the same release.
    with fetch(repository + "/releases/latest") as response:
        match = re.search(r"/releases/tag/([^/?#]+)$", response.geturl())
    if not match:
        raise IOError("Could not resolve the latest yt-dlp release")
    return match.group(1)


def expected_sha256(tag):
    with fetch("{}/releases/download/{}/SHA2-256SUMS".format(repository, tag)) as response:
        sums = response.read().decode("utf-8", "replace")
    for line in sums.splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1].lstrip("*") == name and re.fullmatch(r"[0-9a-f]{64}", parts[0]):
            return parts[0]
    raise IOError("Release {} publishes no checksum for {}".format(tag, name))


try:
    tag = latest_tag()
    expected = expected_sha256(tag)
    url = "{}/releases/download/{}/{}".format(repository, tag, name)
    print("Downloading yt-dlp {} from '{}'...".format(tag, url))
    digest = hashlib.sha256()
    with fetch(url) as response, open(download_name, "wb") as out:
        while True:
            chunk = response.read(1 << 16)
            if not chunk:
                break
            digest.update(chunk)
            out.write(chunk)
    actual = digest.hexdigest()
    if os.path.getsize(download_name) == 0:
        raise IOError("Downloaded yt-dlp file is empty")
    if actual != expected:
        raise IOError("Checksum mismatch for yt-dlp {} (expected {}, got {})".format(tag, expected, actual))
    os.replace(download_name, name)
    print("Download verified (SHA-256 {}).".format(actual))
except Exception as e:
    try:
        if os.path.exists(download_name): os.unlink(download_name)
    except:
        pass
    print("Download failed: " + str(e))
    sys.exit(1)

# Ensure executable bit is NOT set on the data directory script
# to avoid SELinux denials on Android 10+.
# We run it via the native libytdl.so -> libpython.so bridge instead.
try:
    os.chmod(name, 0o600)
except:
    pass

# Create a .sh wrapper as a reference
try:
    with open(scriptdest, "w") as f:
        ytdl_dir = os.getcwd()
        cacert = os.path.abspath(os.path.join(ytdl_dir, "../cacert.pem"))

        f.write("#!/system/bin/sh\n")
        f.write("NATIVE_LIB_DIR=\"{}\"\n".format(native_lib_dir))
        f.write("export PYTHONHOME=\"{}\"\n".format(ytdl_dir))
        f.write("export PYTHONPATH=\"{}/python313.zip\"\n".format(ytdl_dir))
        f.write("export SSL_CERT_FILE=\"{}\"\n".format(cacert))
        f.write("export LD_LIBRARY_PATH=\"$NATIVE_LIB_DIR\"\n")
        f.write("exec \"$NATIVE_LIB_DIR/libytdl.so\" \"$@\"\n")

    os.chmod(scriptdest, 0o700)
    print("Created reference wrapper at {}".format(scriptdest))
except:
    print("Warning: Could not create wrapper")
