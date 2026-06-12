import { Skeleton } from "@/components/ui/skeleton"

export function OpenapiLoadingSkeleton() {
  return (
    <div className="flex flex-1 overflow-hidden">
      <div className="flex w-64 shrink-0 flex-col gap-3 border-r p-4">
        <Skeleton className="h-5 w-32" />
        <div className="flex flex-col gap-2 pt-2">
          {[80, 64, 72, 56, 68, 48, 76].map((w, i) => (
            <Skeleton
              key={i}
              className="h-3.5"
              style={{ width: `${w * 0.25}rem` }}
            />
          ))}
        </div>
      </div>
      <div className="flex flex-1 flex-col gap-6 overflow-auto p-8">
        <div className="flex flex-col gap-3">
          <Skeleton className="h-7 w-48" />
          <Skeleton className="h-4 w-full max-w-lg" />
          <Skeleton className="h-4 w-full max-w-md" />
        </div>
        <div className="flex flex-col gap-3">
          <Skeleton className="h-5 w-36" />
          <Skeleton className="h-24 w-full rounded-lg" />
        </div>
        <div className="flex flex-col gap-3">
          <Skeleton className="h-5 w-36" />
          <Skeleton className="h-24 w-full rounded-lg" />
        </div>
      </div>
    </div>
  )
}
