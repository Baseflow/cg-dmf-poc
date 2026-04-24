import { Skeleton } from "@/components/ui/skeleton"

function TableRowSkeleton() {
  return (
    <div className="flex items-center gap-4 border-b px-4 py-3 last:border-0">
      <Skeleton className="size-4 rounded" />
      <Skeleton className="h-3.5 w-28" />
      <Skeleton className="h-3.5 w-44" />
      <Skeleton className="h-3.5 w-20" />
    </div>
  )
}

export default function Loading() {
  return (
    <div className="flex min-h-svh flex-col gap-4 p-6">
      <div className="flex items-center justify-between">
        <Skeleton className="h-4 w-64" />
        <Skeleton className="h-8 w-28" />
      </div>
      <div className="rounded-lg border">
        {[0, 1, 2].map((i) => (
          <TableRowSkeleton key={i} />
        ))}
      </div>
    </div>
  )
}
