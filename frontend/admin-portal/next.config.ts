import type { NextConfig } from "next"

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080"
    return [
      { source: "/docs", destination: `${backendUrl}/docs` },
      { source: "/docs/:path*", destination: `${backendUrl}/docs/:path*` },
    ]
  },
}

export default nextConfig
