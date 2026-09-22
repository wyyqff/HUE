/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        warm: {
          50: '#f7f9fc',
          100: '#edf2f6',
          200: '#d4dfe8',
          300: '#aec2d1',
          400: '#7898b0',
          500: '#254b68',
          600: '#21445f',
          700: '#173b58',
          800: '#16334c',
          900: '#183047'
        },
        um: {
          primary: '#254b68',
          primary600: '#21445f',
          primary100: '#edf2f6',
          accent: '#a82634',
          cta: '#22c55e',
          bg: '#f7f9fc',
          text: '#183047',
          muted: '#63788b',
        }
      },
      fontFamily: {
        display: ['"PingFang SC"', '"Microsoft YaHei"', '"Noto Sans SC"', 'sans-serif'],
        body: ['"PingFang SC"', '"Microsoft YaHei"', '"Noto Sans SC"', 'sans-serif'],
      },
      boxShadow: {
        um: '0 4px 12px rgba(24, 48, 71, 0.10)',
        umSoft: '0 2px 8px rgba(24, 48, 71, 0.08)',
      },
      borderRadius: {
        um: '16px',
        ummd: '14px',
        umsm: '10px',
      },
    },
  },
  plugins: [],
}
