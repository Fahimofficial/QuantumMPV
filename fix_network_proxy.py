import re

with open("app/src/main/java/com/quantummpv/app/ui/player/PlaybackSession.kt", "r") as f:
    content = f.read()

# Add imports
import_str = "import com.quantummpv.app.data.network.proxy.NetworkStreamingProxy\nimport com.quantummpv.app.data.network.proxy.HlsStreamingProxy\nimport com.quantummpv.app.data.network.proxy.XtreamStreamingProxy"
content = content.replace("import com.quantummpv.app.domain.network.NetworkConnection", import_str + "\nimport com.quantummpv.app.domain.network.NetworkConnection")

old_destroy = """    releaseActiveNetworkStreamLocked()
    releaseAuxiliaryNetworkStreamsLocked()
    observers.clear()"""

new_destroy = """    releaseActiveNetworkStreamLocked()
    releaseAuxiliaryNetworkStreamsLocked()
    NetworkStreamingProxy.stopInstance()
    HlsStreamingProxy.stopInstance()
    XtreamStreamingProxy.stopInstance()
    observers.clear()"""
content = content.replace(old_destroy, new_destroy)

with open("app/src/main/java/com/quantummpv/app/ui/player/PlaybackSession.kt", "w") as f:
    f.write(content)
