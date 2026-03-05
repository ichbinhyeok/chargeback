/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/main/jte/**/*.jte",
    "./src/main/java/**/*.java"
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ["Inter", "sans-serif"]
      },
      colors: {
        primary: {
          50: "#eff6ff",
          100: "#dbeafe",
          500: "#3b82f6",
          600: "#2563eb",
          700: "#1d4ed8"
        },
        neutral: {
          50: "#F6F9FC",
          100: "#E6EAEF",
          200: "#D7DBE4",
          300: "#C4C9D6",
          800: "#1A1F36",
          900: "#0F172A"
        }
      },
      boxShadow: {
        stripe: "0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03)",
        "stripe-hover": "0 10px 15px -3px rgba(0, 0, 0, 0.08), 0 4px 6px -2px rgba(0, 0, 0, 0.04)"
      }
    }
  },
  plugins: []
};
