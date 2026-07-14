import { useState } from 'react';
import { Alert, Box, Button, Paper, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { Link, useNavigate } from 'react-router-dom';
import { register } from '../api/auth';
import { useAuthStore } from '../store/authStore';
import { tokens } from '../theme/theme';
import type { Role } from '../types';

export default function Register() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [role, setRole] = useState<Role>('CUSTOMER');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const setUser = useAuthStore((s) => s.setUser);
  const navigate = useNavigate();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const user = await register({ fullName, email, password, phoneNumber, role });
      setUser(user);
      navigate(role === 'BUSINESS_OWNER' ? '/dashboard' : '/businesses');
    } catch (err: any) {
      setError(err?.response?.data?.error ?? 'Could not create your account. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Box sx={{ maxWidth: 440, mx: 'auto' }}>
      <Typography variant="h3" sx={{ fontSize: 28, mb: 3 }}>
        Create your account
      </Typography>
      <Paper sx={{ p: 3.5, backgroundColor: tokens.surface, border: `1px solid ${tokens.hairline}` }}>
        <form onSubmit={handleSubmit}>
          <Stack spacing={2.5}>
            {error && <Alert severity="error">{error}</Alert>}

            <ToggleButtonGroup
              value={role}
              exclusive
              onChange={(_, val) => val && setRole(val)}
              fullWidth
              size="small"
            >
              <ToggleButton value="CUSTOMER">I join queues</ToggleButton>
              <ToggleButton value="BUSINESS_OWNER">I run a business</ToggleButton>
            </ToggleButtonGroup>

            <TextField label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} required fullWidth />
            <TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required fullWidth />
            <TextField label="Phone (optional)" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} fullWidth />
            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              fullWidth
              helperText="At least 6 characters"
            />
            <Button type="submit" variant="contained" size="large" disabled={loading}>
              {loading ? 'Creating account…' : 'Create account'}
            </Button>
          </Stack>
        </form>
      </Paper>
      <Typography variant="body2" sx={{ color: tokens.inkMuted, mt: 2 }}>
        Already have an account? <Link to="/login" style={{ color: tokens.amber }}>Log in</Link>
      </Typography>
    </Box>
  );
}
