import { useState } from 'react';
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material';
import { Link, useNavigate } from 'react-router-dom';
import { login } from '../api/auth';
import { useAuthStore } from '../store/authStore';
import { tokens } from '../theme/theme';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const setUser = useAuthStore((s) => s.setUser);
  const navigate = useNavigate();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const user = await login({ email, password });
      setUser(user);
      navigate(user.role === 'BUSINESS_OWNER' ? '/dashboard' : '/businesses');
    } catch (err: any) {
      setError(err?.response?.data?.error ?? 'Could not log in. Check your details and try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Box sx={{ maxWidth: 420, mx: 'auto' }}>
      <Typography variant="h3" sx={{ fontSize: 28, mb: 3 }}>
        Log in
      </Typography>
      <Paper sx={{ p: 3.5, backgroundColor: tokens.surface, border: `1px solid ${tokens.hairline}` }}>
        <form onSubmit={handleSubmit}>
          <Stack spacing={2.5}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required fullWidth />
            <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required fullWidth />
            <Button type="submit" variant="contained" size="large" disabled={loading}>
              {loading ? 'Logging in…' : 'Log in'}
            </Button>
          </Stack>
        </form>
      </Paper>
      <Typography variant="body2" sx={{ color: tokens.inkMuted, mt: 2 }}>
        New here? <Link to="/register" style={{ color: tokens.amber }}>Create an account</Link>
      </Typography>
    </Box>
  );
}
