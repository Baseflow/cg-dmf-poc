import { AuthErrorHandler } from "@/components/auth-error-handler"
import { SessionProvider } from "@/components/session-provider"
import { ThemeProvider } from "@/components/theme-provider"
import { TooltipProvider } from "@/components/ui/tooltip"
import type { Metadata } from "next"
import { IBM_Plex_Mono, Source_Sans_3 } from "next/font/google"

import "./globals.css"

const fontSans = Source_Sans_3({
  subsets: ["latin"],
  variable: "--font-sans",
  weight: ["400", "600", "700"],
})

const fontMono = IBM_Plex_Mono({
  subsets: ["latin"],
  variable: "--font-mono",
  weight: ["400", "600", "700"],
})

export const metadata: Metadata = {
  title: "CG DMF Admin Portal",
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${fontSans.variable} ${fontMono.variable} antialiased`}
      >
        <ThemeProvider>
          <TooltipProvider>
            <SessionProvider>
              <AuthErrorHandler />
              {children}
            </SessionProvider>
          </TooltipProvider>
        </ThemeProvider>
      </body>
    </html>
  )
}
