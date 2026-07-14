import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material';
import { getBusiness } from '../api/business';
import { checkIn, cancelTicket } from '../api/queue';
import { useLiveQueue } from '../hooks/useLiveQueue';
import { useAuthStore } from '../store/authStore';
import { tokens } from '../theme/theme';
import type { Business, Ticket } from '../types';
import TicketCard from '../components/TicketCard';

export default function BusinessDetail() {
  const { id } = useParams();
  const businessId = Number(id);
  const user = useAuthStore((s) => s.user);
  const [business, setBusiness] = useState<Business | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [checkingIn, setCheckingIn] = useState(false);
  const { tickets, connected } = useLiveQueue(businessId);

  useEffect(() => {
    getBusiness(businessId).then(setBusiness);
  }, [businessId]);

  const myTicket: Ticket | undefined = tickets.find((t) => t.customerId === user?.userId);

  async function handleCheckIn() {
    setError(null);
    setCheckingIn(true);
    try {
      await checkIn(businessId);
    } catch (err: any) {
      setError(err?.response?.data?.error ?? 'Could not check in right now.');
    } finally {
      setCheckingIn(false);
    }
  }

  async function handleCancel() {
    if (!myTicket) return;
    try {
      await cancelTicket(myTicket.id);
    } catch (err: any) {
      setError(err?.response?.data?.error ?? 'Could not cancel your ticket.');
    }
  }

  if (!business) return <Typography sx={{ color: tokens.inkMuted }}>Loading…</Typography>;

  return (
    <Stack spacing={4}>
      <Box>
        <Typography variant="h3" sx={{ fontSize: 28 }}>{business.name}</Typography>
        <Typography variant="body2" sx={{ color: tokens.inkMuted }}>{business.address}</Typography>
      </Box>

      {error && <Alert severity="error">{error}</Alert>}

      {!user && (
        <Alert severity="info">Log in as a customer to check in to this queue.</Alert>
      )}

      {user && user.role === 'CUSTOMER' && !myTicket && (
        <Paper sx={{ p: 3, backgroundColor: tokens.surface, border: `1px solid ${tokens.hairline}` }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Box>
              <Typography sx={{ fontWeight: 700 }}>
                {business.acceptingCheckIns ? 'Ready when you are' : 'Not accepting check-ins right now'}
              </Typography>
              <Typography variant="body2" sx={{ color: tokens.inkMuted }}>
                {tickets.length} {tickets.length === 1 ? 'person' : 'people'} currently waiting
              </Typography>
            </Box>
            <Button variant="contained" size="large" disabled={!business.acceptingCheckIns || checkingIn} onClick={handleCheckIn}>
              {checkingIn ? 'Checking in…' : 'Check in'}
            </Button>
          </Stack>
        </Paper>
      )}

      {myTicket && (
        <Box>
          <Typography variant="overline" sx={{ color: tokens.mint }}>Your spot</Typography>
          <Box sx={{ mt: 1 }}>
            <TicketCard ticket={myTicket} highlight />
          </Box>
          {myTicket.status === 'WAITING' && (
            <Button variant="outlined" sx={{ mt: 2, borderColor: tokens.hairline, color: tokens.ink }} onClick={handleCancel}>
              Cancel my ticket
            </Button>
          )}
        </Box>
      )}

      <Box>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1.5 }}>
          <Typography variant="overline" sx={{ color: tokens.inkMuted }}>Live queue</Typography>
          <Stack direction="row" spacing={0.7} alignItems="center">
            <Box sx={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: connected ? tokens.mint : tokens.coral }} />
            <Typography variant="overline" sx={{ color: connected ? tokens.mint : tokens.coral, fontSize: 9 }}>
              {connected ? 'live' : 'reconnecting'}
            </Typography>
          </Stack>
        </Stack>

        {tickets.length === 0 ? (
          <Typography variant="body2" sx={{ color: tokens.inkMuted }}>No one is waiting right now.</Typography>
        ) : (
          <Stack spacing={1}>
            {tickets.map((t) => (
              <TicketCard key={t.id} ticket={t} highlight={t.customerId === user?.userId} />
            ))}
          </Stack>
        )}
      </Box>
    </Stack>
  );
}
