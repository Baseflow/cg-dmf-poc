import { Skeleton } from "@/components/ui/skeleton"

function RowSkeleton() {
  return (
    <div className="flex items-center gap-3 rounded-lg border px-4 py-3">
      <div className="flex-1 space-y-1.5">
        <Skeleton className="h-3.5 w-32" />
        <Skeleton className="h-3 w-48" />
      </div>
    </div>
  )
}

export default function Loading() {
  return (
    <div className="flex min-h-svh p-6">
      <div className="flex w-full max-w-sm flex-col gap-4">
        <div className="flex items-center justify-between">
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-8 w-28" />
        </div>
        {[0, 1, 2].map((i) => (
          <RowSkeleton key={i} />
        ))}
      </div>
    </div>
  )
}
