/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        netflix: {
          red: '#E50914',
          'red-hover': '#F40612',
          black: '#141414',
          dark: '#181818',
          card: '#2F2F2F',
          muted: '#6D6D6E',
          light: '#AAAAAA',
        },
      },
      fontFamily: {
        sans: ['"Netflix Sans"', '"Helvetica Neue"', 'Helvetica', 'Arial', 'sans-serif'],
        display: ['"Space Grotesk"', 'sans-serif'],
        body: ['"Manrope"', 'sans-serif'],
        mono: ['"IBM Plex Mono"', 'monospace'],
      },
      backgroundImage: {
        'hero-gradient': 'linear-gradient(to right, rgba(0,0,0,0.85) 40%, transparent 100%)',
        'card-gradient': 'linear-gradient(to top, rgba(0,0,0,0.9) 0%, transparent 60%)',
        'vignette-bottom': 'linear-gradient(to top, #141414 0%, transparent 30%)',
        'nav-gradient': 'linear-gradient(to bottom, rgba(0,0,0,0.7) 0%, transparent 100%)',
      },
      boxShadow: {
        card: '0 0 0 1px rgba(255,255,255,0.08)',
        'card-hover': '0 8px 32px rgba(0,0,0,0.8)',
        panel: '0 18px 60px -24px rgba(0,0,0,0.7)',
      },
      keyframes: {
        shimmer: {
          '0%': { backgroundPosition: '-1000px 0' },
          '100%': { backgroundPosition: '1000px 0' },
        },
        fadeIn: {
          '0%': { opacity: 0 },
          '100%': { opacity: 1 },
        },
        slideUp: {
          '0%': { opacity: 0, transform: 'translateY(20px)' },
          '100%': { opacity: 1, transform: 'translateY(0)' },
        },
        scaleIn: {
          '0%': { opacity: 0, transform: 'scale(0.95)' },
          '100%': { opacity: 1, transform: 'scale(1)' },
        },
      },
      animation: {
        shimmer: 'shimmer 2s infinite linear',
        fadeIn: 'fadeIn 0.4s ease-out forwards',
        slideUp: 'slideUp 0.5s ease-out forwards',
        scaleIn: 'scaleIn 0.3s ease-out forwards',
      },
    },
  },
  plugins: [],
}
