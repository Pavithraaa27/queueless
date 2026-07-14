import { createTheme } from '@mui/material/styles';

// Design tokens — "deli counter / departure board" direction.
// Deep teal-charcoal ground, amber LED accent for live numbers, mint for "active" signal.
export const tokens = {
  bg: '#152321',
  surface: '#1E312E',
  surfaceRaised: '#263B37',
  ink: '#F4EFE4',
  inkMuted: '#9FB6AE',
  amber: '#F5A623',
  amberDim: '#B87A1B',
  mint: '#57D9A3',
  coral: '#E85C4A',
  hairline: 'rgba(244, 239, 228, 0.12)',
};

const theme = createTheme({
  palette: {
    mode: 'dark',
    background: {
      default: tokens.bg,
      paper: tokens.surface,
    },
    primary: {
      main: tokens.amber,
      contrastText: '#12201E',
    },
    secondary: {
      main: tokens.mint,
      contrastText: '#12201E',
    },
    error: {
      main: tokens.coral,
    },
    text: {
      primary: tokens.ink,
      secondary: tokens.inkMuted,
    },
    divider: tokens.hairline,
  },
  typography: {
    fontFamily: '"Inter", "Segoe UI", sans-serif',
    h1: { fontFamily: '"Space Grotesk", "Inter", sans-serif', fontWeight: 600, letterSpacing: '-0.02em' },
    h2: { fontFamily: '"Space Grotesk", "Inter", sans-serif', fontWeight: 600, letterSpacing: '-0.01em' },
    h3: { fontFamily: '"Space Grotesk", "Inter", sans-serif', fontWeight: 600 },
    h4: { fontFamily: '"Space Grotesk", "Inter", sans-serif', fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
    overline: { letterSpacing: '0.14em', fontWeight: 600 },
  },
  shape: {
    borderRadius: 10,
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: { borderRadius: 8, paddingTop: 10, paddingBottom: 10 },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none' },
      },
    },
  },
});

export default theme;
