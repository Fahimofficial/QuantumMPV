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
        accent: "#8b6cff",
        background: "#0c0a14",
        border: "#30264a",
        foreground: "#f7f4ff",
        muted: "#b8afd0",
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
      dark: "#9b82ff",
      light: "#6547d7",
    },
    action: "#805cf5",
    background: {
      dark: "#0c0a14",
      light: "#f7f5ff",
    },
    backgroundImage: {
      dark: "radial-gradient(circle at 85% 0%, rgba(139, 108, 255, 0.14), transparent 34%)",
      light: "radial-gradient(circle at 85% 0%, rgba(101, 71, 215, 0.1), transparent 36%)",
    },
    fonts: {
      body: { name: "IBM Plex Sans", weights: [400, 500, 600, 700] },
      display: { name: "Space Grotesk", weights: [500, 600, 700] },
      mono: "ibm-plex-mono",
    },
    mode: "system",
    radius: "sm",
  },
});
