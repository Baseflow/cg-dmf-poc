import type { NextConfig } from "next"

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return {
      // Checked only after all app routes (including dynamic ones like [...nextauth]).
      // This ensures /api/auth/* is handled by NextAuth before the backend proxy runs.
      fallback: [
        {
          source: "/api/:path*",
          destination: `${process.env.BACKEND_URL ?? "http://localhost:8080"}/:path*`,
        },
      ],
    }
  },
}

export default nextConfig
