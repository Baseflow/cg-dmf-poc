import Link from "next/link"

export default function AboutPage() {
  return (
    <main className="mx-auto flex min-h-svh w-full max-w-2xl flex-col justify-center p-6">
      <h1 className="text-2xl font-semibold">About page</h1>
      <p className="mt-2 text-muted-foreground">
        This route exists because of the file app/about/page.tsx.
      </p>
      <Link href="/" className="mt-4 underline underline-offset-4">
        Back home
      </Link>
    </main>
  )
}