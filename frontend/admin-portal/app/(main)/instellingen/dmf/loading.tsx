import { Skeleton } from "@/components/ui/skeleton"

function FormFieldSkeleton() {
  return (
    <div className="flex flex-col gap-1.5">
      <Skeleton className="h-3.5 w-32" />
      <Skeleton className="h-8 w-full" />
      <Skeleton className="h-3.5 w-3/4" />
    </div>
  )
}

export default function Loading() {
  return (
    <div className="p-6">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <p className="text-sm text-muted-foreground">
          DMF-systeeminstellingen.
        </p>
        <div className="flex flex-col gap-4">
          <FormFieldSkeleton />
          <FormFieldSkeleton />
          <div className="flex items-start gap-2">
            <Skeleton className="mt-px size-4 rounded" />
            <div className="flex flex-col gap-1">
              <Skeleton className="h-3.5 w-40" />
              <Skeleton className="h-3 w-56" />
            </div>
          </div>
          <Skeleton className="h-8 w-24" />
        </div>
      </div>
    </div>
  )
}
