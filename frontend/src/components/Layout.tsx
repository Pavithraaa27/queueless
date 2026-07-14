import { AppBar, Box, Button, Container, Stack, Toolbar, Typography } from '@mui/material';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { tokens } from '../theme/theme';

export default function Layout() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  return (
    <Box sx={{ minHeight: '100vh', backgroundColor: tokens.bg }}>
      <AppBar position="static" elevation={0} sx={{ backgroundColor: tokens.bg, borderBottom: `1px solid ${tokens.hairline}` }}>
        <Toolbar sx={{ maxWidth: 1080, width: '100%', mx: 'auto' }}>
          <Typography
            variant="h4"
            component={Link}
            to="/"
            sx={{ flexGrow: 1, textDecoration: 'none', color: tokens.ink, fontSize: 20, display: 'flex', alignItems: 'center', gap: 1 }}
          >
            <Box component="span" sx={{ color: tokens.amber }}>●</Box>
            QueueLess
          </Typography>
          <Stack direction="row" spacing={1.5} alignItems="center">
            {user ? (
              <>
                <Typography variant="body2" sx={{ color: tokens.inkMuted }}>
                  {user.fullName}
                </Typography>
                {user.role === 'BUSINESS_OWNER' && (
                  <Button size="small" component={Link} to="/dashboard" sx={{ color: tokens.ink }}>
                    Dashboard
                  </Button>
                )}
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => {
                    logout();
                    navigate('/');
                  }}
                  sx={{ borderColor: tokens.hairline, color: tokens.ink }}
                >
                  Log out
                </Button>
              </>
            ) : (
              <>
                <Button size="small" component={Link} to="/login" sx={{ color: tokens.ink }}>
                  Log in
                </Button>
                <Button size="small" variant="contained" component={Link} to="/register">
                  Sign up
                </Button>
              </>
            )}
          </Stack>
        </Toolbar>
      </AppBar>
      <Container maxWidth="md" sx={{ py: 5 }}>
        <Outlet />
      </Container>
    </Box>
  );
}
