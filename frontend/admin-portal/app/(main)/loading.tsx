import { Skeleton } from "@/components/ui/skeleton"

export default function Loading() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-8 p-8">
      <div className="flex flex-col items-center gap-4">
        <Skeleton className="h-12 w-12 rounded-xl" />
        <div className="flex flex-col items-center gap-2">
          <Skeleton className="h-7 w-56" />
          <Skeleton className="h-4 w-72" />
        </div>
      </div>
      <div className="flex w-full max-w-2xl flex-col gap-8">
        {[3, 2].map((cols, i) => (
          <div key={i} className="flex flex-col gap-3">
            <Skeleton className="h-3.5 w-24" />
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {Array.from({ length: cols }).map((_, j) => (
                <Skeleton key={j} className="h-20 w-full rounded-lg" />
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
