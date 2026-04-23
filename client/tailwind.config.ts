import type { Config } from 'tailwindcss'

const config: Config = {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx,js,jsx}'],
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        'status-critical': 'hsl(var(--status-critical))',
        'status-warning':  'hsl(var(--status-warning))',
        'status-success':  'hsl(var(--status-success))',
        'status-info':     'hsl(var(--status-info))',
        'status-live':     'hsl(var(--status-live))',
        'speaker-agent':   'hsl(var(--speaker-agent))',
        'speaker-patient': 'hsl(var(--speaker-patient))',
        dispatch: {
          bg:         '#0b0d10',
          elev:       '#12151a',
          card:       '#161a21',
          cardHi:     '#1c2029',
          line:       '#242935',
          lineSoft:   '#1b1f27',
          text:       '#e7ecf2',
          textDim:    '#9aa3b2',
          textMuted:  '#5a6372',
          slateSoft:  'rgba(148,163,184,0.12)',
        },
        'dispatch-blue': {
          DEFAULT: '#3b82f6',
          soft:    'rgba(59,130,246,0.14)',
          edge:    'rgba(59,130,246,0.45)',
        },
        'dispatch-red': {
          DEFAULT: '#ef4444',
          soft:    'rgba(239,68,68,0.14)',
          edge:    'rgba(239,68,68,0.45)',
        },
        'dispatch-amber': {
          DEFAULT: '#f59e0b',
          soft:    'rgba(245,158,11,0.14)',
        },
        'dispatch-green': {
          DEFAULT: '#22c55e',
          soft:    'rgba(34,197,94,0.14)',
          edge:    'rgba(34,197,94,0.4)',
        },
      },
      fontFamily: {
        sans: ['Pretendard', 'Nanum Gothic', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', '"SFMono-Regular"', 'ui-monospace', 'monospace'],
        ui:   ['"Inter"', 'system-ui', '-apple-system', '"Segoe UI"', 'sans-serif'],
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
      },
      keyframes: {
        'accordion-down': {
          from: { height: '0' },
          to: { height: 'var(--radix-accordion-content-height)' },
        },
        'accordion-up': {
          from: { height: 'var(--radix-accordion-content-height)' },
          to: { height: '0' },
        },
      },
      animation: {
        'accordion-down': 'accordion-down 0.2s ease-out',
        'accordion-up': 'accordion-up 0.2s ease-out',
      },
    },
  },
  plugins: [require('tailwindcss-animate')],
}

export default config
