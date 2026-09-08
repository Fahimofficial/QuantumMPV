import { defineConfig } from "blume";

export default defineConfig({
  title: "mpvRx",
  description:
    "A focused Android media player powered by mpv, with deep playback controls, libraries, streaming, subtitles, and scripting.",
  logo: {
    href: "/",
    image: "/images/icon.png",
    text: "mpvRx",
  },
  banner: {
    content: "mpvRx 2.5.0 is available",
    link: {
      href: "https://github.com/Riteshp2001/mpvRx/releases/latest",
      text: "View release",
    },
  },
  content: {
    root: "content",
  },
  navigation: {
    actions: [
      { href: "/docs", label: "Docs" },
      { href: "https://github.com/Riteshp2001/mpvRx/releases", label: "Releases" },
    ],
    cta: {
      href: "https://github.com/Riteshp2001/mpvRx/releases/latest",
      label: "Download",
    },
    repo: true,
  },
  search: {
    provider: "orama",
    popular: [
      { href: "/docs/getting-started/installation", icon: "download", label: "Install mpvRx" },
      { href: "/docs/playback/controls-and-gestures", icon: "gamepad-2", label: "Player controls" },
      { href: "/docs/customization/custom-commands", icon: "terminal", label: "Scripting API" },
    ],
  },
  ai: {
    api: true,
    llmsTxt: {
      enabled: true,
      details:
        "mpvRx is an Android media player built on libmpv. Use these docs for installation, playback, libraries, streaming, configuration, and the Lua/JavaScript bridge.",
    },
  },
  deployment: {
    output: "static",
  },
  github: {
    owner: "Riteshp2001",
    repo: "mpvRx",
    branch: "master",
    dir: "website/content",
  },
  seo: {
    og: {
      logo: false,
      palette: {
        accent: "#5fd4c7",
        background: "#111314",
        border: "#303538",
        foreground: "#f4f5ef",
        muted: "#aeb5b2",
      },
      titles: {
        "/": "mpvRx - Android media, without the noise",
      },
    },
    software: {
      applicationCategory: "MultimediaApplication",
      license: "AGPL-3.0-or-later",
      name: "mpvRx",
      operatingSystem: "Android 8.0 and newer",
      price: 0,
      sameAs: ["https://github.com/Riteshp2001/mpvRx"],
    },
  },
  theme: {
    accent: {
      dark: "#66d9cc",
      light: "#087f75",
    },
    action: "#f16445",
    fonts: {
      body: { name: "IBM Plex Sans", weights: [400, 500, 600, 700] },
      display: { name: "Space Grotesk", weights: [500, 600, 700] },
      mono: "ibm-plex-mono",
    },
    radius: "sm",
  },
});
