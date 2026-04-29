import { Skeleton } from "@/components/ui/skeleton"

function TableRowSkeleton() {
  return (
    <div className="flex items-center gap-4 border-b px-4 py-3 last:border-0">
      <Skeleton className="size-4 shrink-0 rounded" />
      <Skeleton className="h-3.5 w-28" />
      <Skeleton className="h-3.5 w-48" />
      <Skeleton className="h-3.5 w-32" />
      <Skeleton className="ml-auto h-7 w-16" />
    </div>
  )
}

export default function Loading() {
  return (
    <div className="p-6">
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between gap-4">
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-8 w-28" />
        </div>
        <div className="rounded-lg border">
          {[0, 1, 2].map((i) => (
            <TableRowSkeleton key={i} />
          ))}
        </div>
      </div>
    </div>
  )
}
