import { HTMLAttributes, forwardRef } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const alertVariants = cva('relative w-full rounded-lg border p-4', {
  variants: {
    variant: {
      default: 'bg-background text-foreground',
      info: 'bg-[#d1ecf1] text-[#0c5460] border-[#bee5eb]',
      danger: 'bg-[#f8d7da] text-[#721c24] border-[#f5c6cb]',
      destructive: 'bg-[#f8d7da] text-[#721c24] border-[#f5c6cb]',
      success: 'bg-[#d4edda] text-[#155724] border-[#c3e6cb]',
      warning: 'bg-[#fff3cd] text-[#856404] border-[#ffeeba]',
    },
  },
  defaultVariants: { variant: 'default' },
})

interface AlertProps
  extends HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof alertVariants> {}

const Alert = forwardRef<HTMLDivElement, AlertProps>(
  ({ className, variant, ...props }, ref) => (
    <div ref={ref} role="alert" className={cn(alertVariants({ variant }), className)} {...props} />
  )
)
Alert.displayName = 'Alert'

const AlertDescription = forwardRef<HTMLParagraphElement, HTMLAttributes<HTMLParagraphElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn('text-sm [&_p]:leading-relaxed', className)} {...props} />
  )
)
AlertDescription.displayName = 'AlertDescription'

export { Alert, AlertDescription }
