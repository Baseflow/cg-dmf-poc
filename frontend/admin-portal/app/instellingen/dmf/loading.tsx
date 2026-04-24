import { Skeleton } from "@/components/ui/skeleton"

function FormFieldSkeleton() {
  return (
    <div className="flex flex-col gap-1.5">
      <Skeleton className="h-3.5 w-32" />
      <Skeleton className="h-9 w-full" />
    </div>
  )
}

export default function Loading() {
  return (
    <div className="flex min-h-svh p-6">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <Skeleton className="h-4 w-64" />
        <div className="flex flex-col gap-4">
          <FormFieldSkeleton />
          <FormFieldSkeleton />
          <div className="flex items-center gap-2">
            <Skeleton className="size-4 rounded" />
            <Skeleton className="h-4 w-40" />
          </div>
        </div>
        <Skeleton className="h-8 w-24" />
      </div>
    </div>
  )
}
